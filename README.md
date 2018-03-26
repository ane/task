# task [![CircleCI](https://circleci.com/gh/ane/task/tree/master.svg?style=svg&circle-token=e18d6f0c73c42d24828e306f6c7de1fc639cbddd)](https://circleci.com/gh/ane/task/tree/master) [![Clojars Project](https://img.shields.io/clojars/v/com.github.ane/task.svg)](https://clojars.org/com.github.ane/task)

Simple, functional and value-oriented concurrency primitives for Clojure. 

  * [API Documentation](https://ane.github.io/task/api/)
  * [User Guide](https://ane.github.io/task/)

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
(def my-var (task/run 123))

@(my-var) ; => 123
```

### `then` - Apply a function to some asynchronous value

`then` applies a function to the result of another task.

``` clojure
(def async-var (task/run (Thread/sleep 1000)
                    "asdf"))
                    
@(task/then str/upper-case async-var)
; => "ASDF"
```

### `compose` - Compose two asynchronous computations

`compose` applies a function producing a task on the result of a a task, and chains their execution together.

``` clojure
(defn comp1 [x] 
  (task/run (Thread/sleep 1000)
       (inc x)))

(defn comp2 [x]
  (task/run (Thread/sleep 1000)
       (* 2 x)))
       
@(task/compose comp2 (comp1 4))
; => 10
```

### `for` - Compose tasks without the boilerplate

`for` lets you apply `compose` and `then` without the boilerplate. It behaves like `let`, it binds
the symbols in the bindings to futures. Once all futures are complete, it evaluates the body.

``` clojure
@(task/for [x (future 1)
            y (task/run 2)
            c (task/run (Thread/sleep 1000)
                        7)]
  (+ x y c))
  
; => 10
```

### An example: HTTP requests

In the example below, the promise returned by `http/get` is automatically converted into a task. The
function then extracts the body, parses it into JSON, gets the title and uppercases it. This
executes asynchronously in another thread, so we have to deref it to print its results.

``` clojure
(def request
  (task/then
   (fn [data] (-> data
                  :body
                  (cheshire/parse-string true)
                  :title
                  str/upper-case))
   (http/get "http://jsonplaceholder.typicode.com/posts/3")))

(println @request)
```

#### Using `compose` to get the result from a POST operation

Here we chain two HTTP requests together. First we POST to `api-url` with some example data, then
we extract the `Location` header, and execute a GET request to that URL. By using compose we don't
end up with nested tasks. 

``` clojure
(defn post-and-fetch
  [title content]
  (task/compose
   (fn [response]
     (http/get (-> response :headers :location)))
   (http/post api-url
              {:body (cheshire/generate-string {:userId 123 :title title :body content})})))
              
@(post-and-fetch "Sample title" "Sample content!")

;; => {:opts {...}, :body "this would be the result", :headers {...}, :status 200}

```

### Error handling

*See [Error handling](https://ane.github.io/task/#recovering-from-errors) in the user guide*

task provides advanced facilities for error handling. Currently the principal method is `recover`
which accepts a task and a function. The function is passed any exception thrown from the task and
then the value returned by the function. `recover` produces a new task:

``` clojure
(def boom (task/run (/ 1 0)))
(def incremented (task/then inc boom))
@(task/recover incremented
          (fn [ex]
            (println "caught exception: " (.getMessage ex))
            123))
;; => 123
```

## License

Copyright © Antoine Kalmbach. All rights reserved.

Distributed under the [Apache License](https://www.apache.org/licenses/LICENSE-2.0) either version 2.0 or (at
your option) any later version.
