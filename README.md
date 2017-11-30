# task [![CircleCI](https://circleci.com/gh/ane/task/tree/master.svg?style=svg&circle-token=e18d6f0c73c42d24828e306f6c7de1fc639cbddd)](https://circleci.com/gh/ane/task/tree/master) [![Clojars Project](https://img.shields.io/clojars/v/com.github.ane/task.svg)](https://clojars.org/com.github.ane/task)

Simple and functional concurrency primitives for Clojure. 

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

## Examples

The task API is built on basic building blocks, `run`, `then`, `compose` and `for`.

### `run` - Compute some value asynchronously

Use the standard `deref`/`@` to block the current thread and await the result.

``` clojure
(def my-var (run 123))

@(my-var) ; => 123
```

### `then` - Apply a function to some asynchronous value

`then` applies a function to the result of another task.

``` clojure
(def async-var (run (Thread/sleep 1000)
                    "asdf"))
                    
@(then str/upper-case async-var)
; => "ASDF"
```

### `compose` - Compose two asynchronous computations

`compose` applies a function producing a task on the result of a a task, and chains their execution together.

``` clojure
(defn comp1 [x] 
  (run (Thread/sleep 1000)
       (inc x)))

(defn comp2 [x]
  (run (Thread/sleep 1000)
       (* 2 x)))
       
@(compose comp2 (comp1 4))
; => 10
```

### `for` - Compose tasks without the boilerplate

`for` lets you apply `compose` and `then` without the boilerplate. It behaves like `let`, it binds
the symbols in the bindings to futures. Once all futures are complete, it evaluates the body.

``` clojure
@(for [x (future 1)
       y (run 2)
       c (run (Thread/sleep 1000)
              7)]
  (+ x y c))
  
; => 10
```

### A concrete example: do a HTTP request asynchronously and operate on its results

``` clojure
(ns my-program.core
  (:require [org.httpkit.client :as http]
            [task.core :as task]
            [cheshire.core :as cheshire]))
            
; do a request - http-kit calls return promises, these are converted into tasks.
(defn do-http-request-async
  [data]
  (task/then (http/post "http://host.com/api" (cheshire/generate-string data))
              (comp :result cheshire/parse-string)))

; compute something expensive
(defn compute-result
  [v]
  (task/run (Thread/sleep 1000)
             (* v 1000)))

; combine tasks
(defn request-and-compute
  [data]
  (task/for [response  (do-http-request-async data)
             computed  (compute-result response)
             blah      (future 100)] ; interop!

    (+ 100 computed blah)))

; blocks until it's ready
@(request-and-compute {:some-value 1})
```

## License

Copyright © 2017 Antoine Kalmbach. All rights reserved.

Distributed under the [Apache License](https://www.apache.org/licenses/LICENSE-2.0) either version 2.0 or (at
your option) any later version.
