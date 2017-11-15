(ns task.core
  (:refer-clojure :exclude [for sequence] :as core)
  (:import [java.util.concurrent CompletableFuture ForkJoinPool TimeoutException TimeUnit]))

(defprotocol Task
  (task->future [this] "convert self into CompletableFuture"))

(defn- future->task
  [^CompletableFuture fut]
  (reify
    Task
    (task->future [_] fut)

    clojure.lang.IBlockingDeref
    (deref [_ timeout-ms timeout-val]
           (try
             (.get fut timeout-ms TimeUnit/MILLISECONDS)
             (catch TimeoutException e timeout-val)))

    java.util.concurrent.Future
    (get [_] (.get fut))
    (get [_ timeout unit] (.get timeout unit))
    (isCancelled [_] (.isCancelled fut))
    (isDone [_] (.isDone fut))
    (cancel [_ interrupt] (.cancel fut interrupt))))


(defn- supplier
  [func]
  (reify java.util.function.Supplier
    (get [this] (func))))

(defn- function
  [func]
  (reify java.util.function.Function
    (apply [this t] (func t))
    (andThen [this after]
      (function #(.apply after (.apply this %1))))
    (compose [this before]
      (function #(.apply this (.apply before %1))))))

(defn- fn->future
  [func & [executor]]
  (CompletableFuture/supplyAsync (supplier func) (or executor (ForkJoinPool/commonPool))))

(defmacro run
  "Execute `body` inside the default [[java.util.concurrent.ForkJoinPool ForkJoinPool]]
  executor."
  ([& body] `(future->task (fn->future (fn [] ~@body)))))

(defmacro run-in
  "Execute `body` inside the supplied [[java.util.concurrent.ExecutorService ExecutorService]]."
  ([executor & body] `(future->task (fn->future (fn [] ~@body)) ~executor)))

(defn now
  "Create a task that completes immediately with `value`."
  [value]
  (future->task (CompletableFuture/completedFuture value)))

(defn- ensure-task
  [obj]
  (cond (satisfies? Task obj) obj
        ; this is a promise or future
        (and (instance? clojure.lang.IPending obj)
             (instance? clojure.lang.IDeref obj))
        (if (realized? obj)
          (now (deref obj))
          (run (deref obj)))
        ; clojure futures will be caught before this step
        (instance? java.util.concurrent.Future obj)
        (if (.isDone obj)
          (now (.get obj))
          (run (.get obj)))))

(def common-pool (ForkJoinPool/commonPool))

(defn then
  "Apply a function to the result of `task`.

  Applies `func` to the result of `task`, returns a new task. Optionally, with a third argument
  `executor`, run the task in that [[java.util.concurrent.ExecutorService ExecutorService]]."
  [func task & [executor]]
  (future->task (.thenApplyAsync ((comp task->future ensure-task) task) (function func)
                                 (or executor common-pool))))

(defn compose
  "Chain two tasks together. Applies `func`, which is a function returning a task,
to the result of `task`. This returns a new task. Should be used when the function
  to [[then]] returns a task, which would result in a task inside a task. Given an executor, use that."
  ([func task & [executor]]
   (future->task (.thenComposeAsync ((comp task->future ensure-task) task)
                                    (function (comp task->future ensure-task func))
                                    (or executor common-pool)))))

(defn cancel
  "Attempt to cancel `task`."
  [task]
  (future-cancel task))

(defmacro for
  "Chain multiple tasks together. Returns a new task that evaluates when all the
  tasks are ready together. The task evaluates to `body`."
  [bindings & body]
  `(for* ,common-pool ~bindings ~@body))

(defmacro for-in
  "Chain multiple tasks together. Returns a new task that evaluates when all the
  tasks are ready together. The task evaluates to `body`. Uses the `executor` executor."
  [executor bindings & body]
  `(for* ~executor ~bindings ~@body))

(defmacro for*
  [executor bindings & body]
  (let [groups# (if (<= 2 (count bindings))
                  (partition 2 bindings)
                  bindings)
        rest# (next groups#)
        [[sym# fut#] & _] groups#]
    (if rest#
      `(compose (fn [~sym#]
                  ~(if (next rest#)
                     `(for* ~executor [~@(mapcat identity rest#)] ~@body)
                     (let [[[tsym# tfut#]] (or rest# groups#)]
                       `(then (fn [~tsym#] ~@body) ~tfut# ~executor))))
                ~fut#
                ~executor)
      `(then (fn [~sym#] ~@body) ~fut# ~executor))))

(defn cancelled?
  "Return true if `task` was cancelled."
  [task]
  (future-cancelled? task))

(defn sequence
  "Turn a sequence of tasks into a task of a sequence. Returns a new task returning a vector
  of all the results of each task. The task evaluates when all the tasks evaluate."
  [tasks]
  (reduce (fn [fr fa]
            (for [r fr
                  a fa]
              (println r)
              (conj r a)))
          (now [])
          tasks))
