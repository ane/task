(ns task.core
  (:refer-clojure :exclude [for] :as core)
  (:import [java.util.concurrent CompletableFuture CompletionStage TimeoutException TimeUnit]))

(defprotocol Task
  (task->future [this] "convert self into CompletableFuture"))

(defn- create
  [^CompletableFuture fut]
  (reify
    Task
    (task->future [_] fut)
    clojure.lang.IDeref
    (deref [_] (.get fut))
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
  (if executor
    (CompletableFuture/supplyAsync (supplier func) executor)
    (CompletableFuture/supplyAsync (supplier func))))

(defmacro run
  "Execute `body` inside the default [[java.util.concurrent.ForkJoinPool ForkJoinPool]]
  executor."
  ([& body] `(create (fn->future (fn [] ~@body)))))

(defmacro run-in
  "Execute `body` inside the supplied [[java.util.concurrent.ExecutorService ExecutorService]]."
  ([executor & body] `(create (fn->future (fn [] ~@body)) ~executor)))

(defn then
  "Apply a function to the result of `task`.

Applies `func` to the result of `task`, returns a new task. Optionally, with a third argument
`executor`, run the task in that [[java.util.concurrent.ExecutorService ExecutorService]]."
  ([func task]
   (create (.thenApplyAsync (task->future task) (function func))))
  ([func task executor]
   (create (.thenApplyAsync (task->future task) (function func) executor))))

(defn compose
  "Chain two tasks together. Applies `func`, which is a function returning a task,
to the result of `task`. This returns a new task. Should be used when the function
to [[then]] returns a task, which would result in a task inside a task."
  ([func task]
   (create (.thenComposeAsync (task->future task) (function (comp task->future func))))))

(defn cancel
  "Attempt to cancel `task`."
  [task]
  (future-cancel task))

(defmacro for
  "Chain multiple tasks together. Applies [[compose]] left-to-right and then runs `body`. Returns a new task that evaluates
when all the tasks are ready together."
  [bindings & body]
  (let [groups# (if (<= 2 (count bindings))
                  (partition 2 bindings)
                  bindings)
        rest# (next groups#)
        [[sym# fut#] & _] groups#]
    (if rest#
      `(compose (fn [~sym#]
                  ~(if (next rest#)
                     `(gather [~@(mapcat identity rest#)] ~@body)
                     (let [[[tsym# tfut#]] (or rest# groups#)]
                       `(then (fn [~tsym#] ~@body) ~tfut#))))
                ~fut#)
      `(then (fn [~sym#] ~@body) ~fut#))))

(defn cancelled?
  "Return true if `task` was cancelled."
  [task]
  (future-cancelled? task))

