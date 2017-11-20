# Execution Model

In the task model, the execution model is intentionally kept opaque, but not hidden. This means that
by default, the user doesn't need to worry about where -- in which thread -- code is executed. 

What the user needs to know, to *get started*, is:

* all code is asynchronous and non-blocking, and
* `deref` or `@` will block the *current thread*.

The concurrency model is left to the *executor*.

A JVM executor is an abstraction for thread execution. This can be a thread pool with 64 threads per
core. Or more. Or less. By default, task uses
[ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html), a
work-stealing scheduler, that attempts to find the best use for each of its threads.

The reason to default to the ForkJoinPool, in addition for it being perfomant, is that it kind of
makes tasks appear lightweight (a bit like, but not similar to, fibers), as the user does not
intentionally think that there is a one-to-one correspondence between each task and a thread. This
means that you can have several hundred tasks executing concurrently for only a handful of threads!
The ForkJoinPool handles the heavy lifting here: some tasks will complete faster, some slower, so
the point is to have this work-stealing algorithm that makes use of idle threads.

It is often the case that such behavior is not desirable. Which is why the executor can be
overridden via two methods:

* implicitly by using dynamic binding, or
* explicitly by using parameters to task-executing functions.

Dynamic binding is a standard Clojure method for doing dynamic scope. This is how it works.

``` clojure
(def ^:dynamic foo "hello")

(println foo) ; prints "hello"

(binding [foo "hi"]
  (println foo)) ; prints "ho"
```

Using this technique lets us freely swap the executor. The var for this is [[*pool*]].

``` clojure
TODO: example
```

The JVM offers
[several](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) you
can freely chose from. This document is not a guide on their details, the user is left to explore
that page or the Internet for more examples. 

Usually, in 99% of cases, the default `ForkJoinPool` executor is just fine. But the option to
customize is there.

## Blocking pitfalls

To get the value out of a task, you deref it. This blocks the executing thread. It is generally safe
to do so, but choosing the wrong kind of executor might yield strange behaviour.

For example, when using a [fixed thread
pool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executors.html#newFixedThreadPool-int-)
like this, it might not be obvious that once you run out of threads, the whole program will grind to a halt.

This is particularly nasty if you do it inside a HTTP server, e.g. you use the same thread pool for
handling incoming web requests and database access. If your database hangs and takes time to answer
the questions, the web server, which shares the thread pool, cannot spawn new threads to handle
incoming requests and the web server will appear unresponsive!
