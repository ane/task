(ns task.core
  "This namespace contains the core components of the task libary.

  For task creation, see [[run]].

  For task combinators, see [[then]], [[compose]], [[for]] and [[sequence]].

  To use custom executors, you have either two options: implicitly via dynamic binding,
or explicitly using an additional parameter.


  By default, the executor is bound to the variable [[*pool*]]. This can be re-bound dynamically
  like this:

```clojure
(binding [*pool* (Executors/newFixedThreadPool 4)]
  @(then inc (task/run 1234)))
```

  By default, `*pool*` is bound to [[common-pool]].

  Otherwise, you can use the parameter versions. The explicit versions have `-in` appended to their names.
    * [[run]] and [[run-in]]
    * [[then]] and [[then-in]]
    * [[compose]] and [[compose-in]]
    * [[for]] and [[for-in]]
    * [[sequence]] and [[sequence-in]]

  For more information see the documentation about the [execution model](./03-executors.md).
  "
  (:refer-clojure :exclude [for sequence] :as core)
  (:import [java.util.concurrent CompletableFuture Executor ForkJoinPool TimeoutException TimeUnit Executors]))

; boring Java FunctionalInterface crap
(defn ^:no-doc fn->Supplier
  [func]
  (reify java.util.function.Supplier
    (get [this] (func))))

(defn ^:no-doc fn->Function
  [func]
  (reify java.util.function.Function
    (apply [this t] (func t))
    (andThen [this after]
      (fn->Function #(.apply after (.apply this %1))))
    (compose [this before]
      (fn->Function #(.apply this (.apply before %1))))))

(defprotocol Task
  (task->future [this] "Convert this task into a CompletableFuture.")
  (done? [this] "Has the task completed in any fashion?")
  (cancel [this] "Attempt to cancel the future, returns true if it cancelled.")
  (cancelled? [this] "Was the task cancelled?")
  (complete! [this] [this value] "Complete the task with some value if it hasn't completed already.")
  (failed? [this] "Did the task complete with an exception of any kind?")
  (failure [this] "If the task has completed exceptionally, get the Throwable that produced the exceptional completion.")
  (else [this value] "Get the value of the task if it's complete. Otherwise return `value`. This is non-blocking.")
  (fail! [this throwable] "Force the task to fail with `throwable` whether or not already completed.")
  (force! [this value] "Force the task to return `value` whether or not already completed.")
  (recover [this f]
    "Recover possible failures in `task`. Returns a new task. This
  task evaluates to two possible values. If the task completes with an
  exception, this exception is passed to `func`, and the task
  evaluates to its result. If the task completes normally, the task
  will evaluate to that result."))
  
(defn future->task
  "Convert a CompletableFuture into a Task."
  [^CompletableFuture fut]
  (reify
    Task
    (task->future [_]               fut)
    (done?        [this]            (.isDone fut))
    (cancel       [this]            (.cancel fut true))
    (cancelled?   [this]            (.isCancelled fut))
    (complete!    [this]            (.complete fut nil))
    (complete!    [this value]      (.complete fut value))
    (force!       [this value]      (.obtrudeValue fut value))
    (recover      [this f]          (future->task (.exceptionally fut (fn->Function f))))
    (else         [this value]      (.getNow fut value))
    (failed?      [this]            (.isCompletedExceptionally fut))
    (failure      [this]            (when (failed? this)
                                      (let [fail (try @this (catch Exception e e))]
                                        (or (.getCause fail) fail))))
    (fail!        [this throwable]  (.obtrudeException fut throwable))

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

(def common-pool
  "An alias for the [ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html) Executor."
  (ForkJoinPool/commonPool))

(def ^:dynamic *pool*
  "The dynamic binding for the executor. Re-bind this var to set a new executor
  for that scope. Defaults to [ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html)."
  common-pool)

(defn now
  "Create a task that completes immediately with `value`."
  [value]
  (future->task (CompletableFuture/completedFuture value)))

(defn fn->future
  "Convert any function into a CompletableFuture."
  [func & [executor]]
  (CompletableFuture/supplyAsync (fn->Supplier func) (or executor *pool*)))

(defmacro run
  "Create a task. Runs `body` according to the execution model. Produces a [[Task]] that evaluates
to the result of `body`.

  This *may* mean running in another thread.
  The default behaviour is the executor bound to [[*pool*]] which `body` inside
  the [ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html)
  executor."
  ([& body] `(future->task (fn->future (fn [] ~@body)))))

(defmacro run-in
  "Execute `body` inside the supplied [ExecutorServce](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html)."
  ([executor & body] `(future->task (fn->future (fn [] ~@body) ~executor))))

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

(declare sequence sequence-in)
(defn then-in
  "Like [[then]], but use the explicit executor given in `executor`."
  ([executor f]
   (fn [task] (then-in executor f task)))
  ([executor f task]
   (future->task (.thenApplyAsync ((comp task->future ensure-task) task) (fn->Function f)
                                  (or executor *pool*))))
  ([executor f task & more]
   (sequence-in executor (map #(then-in executor f %) (into [task] more)))))

(defn then
  "Apply a function to the result of `task`. Returns a new task.

  With one argument, creates a function that accepts a task, and applies `func` on that task.
  
  With two arguments, applies `func` to `task`.
  
  With more than two arguments, returns a task of a vector with all the results of `func` applied to
  the arguments. 

  By default, it uses the executor bound to [[*pool*]].

  See [[then-in]]."
  ([func] (fn [task] (then-in *pool* func task)))
  ([func task] (then-in *pool* func task))
  ([func task & more] (sequence (map #(then func %) (into [task] more)))))

(defn compose-in
  "Like [[compose]], but use the explicit executor given in `executor`."
  ([executor f] (fn [task] (compose-in executor f task)))
  ([executor f task]
   (future->task (.thenComposeAsync ((comp task->future ensure-task) task)
                                    (fn->Function (comp task->future ensure-task f))
                                    executor)))
  ([executor f task & more]
   (sequence-in executor (map #(compose-in executor f %) (into [task] more)))))

(defn compose
  "Chain two tasks together. Applies `func`, which is a function returning a task,
  to the result of `task`. This returns a new task.

  Should be used when the function to [[then]] returns a task, which would
  otherwise result in a task inside a task.

  With one argument, create a function that accepts a task. With two args, apply `func` to
  `task` directly. With more than two args, return a task that completes with a list of `func` 
  composed with the results.

  By default, it uses the executor bound to [[*pool*]].

  See also [[compose-in]]."
  ([func] (fn [task] (compose-in *pool* func task)))
  ([func task] (compose-in *pool* func task))
  ([func task & more] (sequence (map #(compose func %) (into [task] more)))))

(defmacro ^:no-doc for*
  [executor bindings & body]
  {:pre [(vector? bindings) (even? (count bindings))]}
  (let [groups# (if (<= 2 (count bindings))
                  (partition 2 bindings)
                  bindings)
        rest# (next groups#)
        [[sym# fut#] & _] groups#]
    (if rest#
      `(compose-in ~executor
                   (fn [~sym#]
                     ~(if (next rest#)
                        `(for* ~executor [~@(mapcat identity rest#)] ~@body)
                        (let [[[tsym# tfut#]] (or rest# groups#)]
                          `(then-in ~executor (fn [~tsym#] ~@body) ~tfut#))))
                   ~fut#)
      `(then-in ~executor (fn [~sym#] ~@body) ~fut#))))

(defmacro for
  "Chain multiple tasks together. Returns a new task that evaluates when all the
  tasks are ready together. The task evaluates to `body`. Uses the executor bound to
  [[*pool*]] by default."
  [bindings & body]
  `(for* ~`*pool* ~bindings ~@body))

(defmacro for-in
  "Chain multiple tasks together. Returns a new task that evaluates when all the
  tasks are ready together. The task evaluates to `body`. Uses the `executor` executor."
  [executor bindings & body]
  `(for* ~executor ~bindings ~@body))


(defn void
  "Create an incomplete task with nothing in it which will never complete."
  []
  (future->task (CompletableFuture.)))

(defn sequence-in
  "Like [[sequence]], but pass explicit `executor`."
  [executor tasks]
  (reduce (fn [fr fa]
            (for-in executor
                    [r fr
                     a fa]
                    (conj r a)))
          (now [])
          tasks))

(defn sequence
  "Turn a sequence of tasks into a task of a sequence. Returns a new task returning a vector
  of all the results of each task. The task evaluates when all the tasks evaluate.

  Rebind [[*pool*]] to change the executor."
  [tasks]
  (sequence-in *pool* tasks))

(defn failed
  "Create a new task that fails with `t`."
  [^java.lang.Throwable t]
  (let [task (void)]
    (.completeExceptionally (task->future task) t)
    task))

(defn traverse-in
  "Like [[traverse]] but with explicit executor."
  [executor xs f]
  (reduce (fn [fr a]
            (for-in executor
                    [r fr
                     b (f a)]
                    (conj r b)))
          (now [])
          xs))

(defn traverse
  "Map over a sequence with a function that produces a task, and capture the result of the whole operation in a task.
  Given `f` which is a function from something to a task, `traverse` produces a task that evaluates into a list as if
  f were `map`d over it."
  [xs f]
  (traverse-in *pool* xs f))
