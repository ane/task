# Overview

Tasks provide simple and functional concurrency primitives for Clojure. Tasks represent asynchronous
computation as *pure values*, and favours function composition over
[callbacks](https://en.wikipedia.org/wiki/Callback_\(computer_programming\)) or promises.

Task promotes the use of combinators like [[then]] and [[compose]] to create intuitive and
referentially transparent pipelines.

For example, a potential use case is doing HTTP requests. If we want to operate on the value of a
GET request, and apply a data transformation, this is what you need to do.

```clojure
@(task/then
  (fn [data] (->> cheshire/parse-string
                  :my-interesting-attribute
                  clojure.string/upper-case))
  (http/get "value"))
```

The snippet uses [http-kit](http://www.http-kit.org/) to launch a GET request asynchronously. The
http-kit promise is automatically converted into a task. Once the promise completes, we parse the
string into JSON (`cheshire/parse-string`) and then extract our attribute from it, and then
uppercase the results.

All of this is executed asynchronously, so we wrap the call to `then` with a call to `@`, which
calls [deref](http://clojuredocs.org/clojure.core/deref), a Clojure standard library function, to
*await* its result in the current thread. This blocks the execution of the current thread an and
returns the result of the computation.

## Key features

  * **Value-oriented**: tasks are just eventual values. No more callbacks, regular `deref`/`@` is all you need. 
  * **Functional**: tasks are composable. Tasks come with a set of operations that lets you compose
    and combine them in a functional manner.
  * **Interoperable**: Any Clojure `future`/`promise` can be converted into a task, and vice versa.
  * **Asynchronous**: tasks are always asynchronous. Tasks default to the
    [ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html?is-external=true)
    executor. 
  * **Customizable**: If you wish to control the execution model, you can define your own
    [ExecutorService](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html).
  * **Performant**: The library leverages the standard [Java 8 Concurrency
    API](https://docs.oracle.com/javase/8/docs/technotes/guides/concurrency/changes8.html), a
    powerful framework for concurrent computation.

Head over to the [User Guide](./02-guide.md) to learn more!

