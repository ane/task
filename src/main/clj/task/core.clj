(ns task.core
  (:refer-clojure :exclude [for sequence] :as core)
  (:import [java.util.concurrent CompletableFuture ExecutionException ForkJoinPool TimeoutException TimeUnit]))

(defprotocol Task
  (task->future [this] "convert self into CompletableFuture"))

(defn future->task
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


(defn supplier
  [func]
  (reify java.util.function.Supplier
    (get [this] (func))))

(defn function
  [func]
  (reify java.util.function.Function
    (apply [this t] (func t))
    (andThen [this after]
      (function #(.apply after (.apply this %1))))
    (compose [this before]
      (function #(.apply this (.apply before %1))))))

(defn fn->future
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

(defn done?
  "Has the task completed in any fashion?"
  [task]
  (.isDone (task->future task)))

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
  "Apply a function to the result of `task`. Returns a new task.

  With one argument, creates a function that accepts a task, and applies `func` on that task.
  
  With two arguments, applies `func` to `task`.

  Optionally, with a third argument `executor`, run the task in
  that [[java.util.concurrent.ExecutorService ExecutorService]]."
  ([func] (fn [task] (then func task)))
  ([func task] (then func task common-pool))
  ([func task executor]
   (future->task (.thenApplyAsync ((comp task->future ensure-task) task) (function func)
                                  (or executor common-pool)))))

(defn compose
  "Chain two tasks together. Applies `func`, which is a function returning a task,
  to the result of `task`. This returns a new task.

  compose is useful when the function that you want to apply on the task returns a new task.
  This would result in a task inside a task, so you would need to double deref it. So, `compose`
  solves this for you.

  With one argument, create a function that accepts a task. With two args, apply `func` to
  `task` directly.

  Should be used when the function to [[then]] returns a task, which would
  otherwise result in a task inside a task. Given an executor, use that."
  ([func] (fn [task] (compose func task common-pool)))
  ([func task] (compose func task common-pool))
  ([func task executor]
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


(defn void
  "Create an incomplete task with nothing in it."
  []
  (future->task (CompletableFuture.)))

(defn sequence
  "Turn a sequence of tasks into a task of a sequence. Returns a new task returning a vector
  of all the results of each task. The task evaluates when all the tasks evaluate."
  [tasks]
  (reduce (fn [fr fa]
            (for [r fr
                  a fa]
              (conj r a)))
          (now [])
          tasks))

(defn complete!
  "Complete the task with some value if it hasn't completed already."
  [task value]
  (.complete (task->future task) value))

(defn failed
  "Create a new task that fails with `t`."
  [^java.lang.Throwable t]
  (let [task (void)]
    (.completeExceptionally (task->future task) t)
    task))

(defn failed?
  "Did the task complete with an exception of any kind?"
  [task]
  (.isCompletedExceptionally (task->future task)))

(defn failure
  "Get the exception with which the task completed exceptionally, if any."
  [task]
  (when (failed? task)
    (let [fail (try @task (catch Exception e e))]
      (or (.getCause fail) fail))))

(defn else
  "Get the value of `task` if it's complete, otherwise return
  `value`."
  [task value] (.getNow (task->future task) value))

(defn fail!
  "Force the value of task to fail with `t` whether or not already completed."
  [task ^java.lang.Throwable t]
  (.obtrudeException (task->future task) t))

(defn force!
  "Force the value of task to return `value` whether or not already completed."
  [task value]
  (.obtrudeValue (task->future task) value))

(defn recover
  "Recover possible failures in `task`. Returns a new task. This task evaluates to
  two possible values:

  * If the task completes with an exception, this exception is passed to `func`, and the task
  evaluates to its result.
  * If the task completes normally, the task will evaluate to that result."
  [task func]
  (let [jf (task->future task)]
    (future->task (.exceptionally jf (function func)))))
