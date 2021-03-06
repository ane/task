(ns task.tutorial
  (:require [task.core :as task])
  (:import (java.util.concurrent Executors)))

(def for-1
;; tag::for[]
  (task/for [x (task/run 123)
             y (future 123)
             z (task/run (Thread/sleep 1000) 4)]
            (+ x y z))
;; end::for[]
  )

(def sequence1
;; tag::sequence1[]
  (task/sequence [(task/run 123) (future 9) (task/run (Thread/sleep 100) "hello")])
  ; => [123 9 "hello]
;; end::sequence1[]
  )

(def traverse1
;; tag::traverse1[]
  (task/traverse
    [1 2 3 4 5 6]
    (fn [x] (task/run (* 2 x))))
  ; => [2 4 6 8 10 12]
;; end::traverse1[]
  )

(def multiple-executors
;; tag::multiple-executors[]
  (let [pool1 (Executors/newFixedThreadPool 4)
        pool2 (Executors/newCachedThreadPool)
        init (task/run-in pool1
                          (Thread/sleep 1000)
                          (println "we start in" (.getName (Thread/currentThread)))
                          'blaa)]
    (task/then-in
      pool2
      (fn [x]
        (println "...but we end in" (.getName (Thread/currentThread)))) init))
  ; prints:
  ; we start in pool-8-thread-1
  ; ...but we end in pool-9-thread-1
;; end::multiple-executors[]
  )

(def rebind
  ;; tag::rebind1[]
  (binding [task/*pool* (Executors/newCachedThreadPool)]
    (task/run (println "Hello!")
              1)))
  ;; end::rebind1[]

(def whoops
  ;; tag::whoops[]
  (binding [task/*pool* (Executors/newFixedThreadPool 1)]
    (let [nothing (task/void)]
      (task/compose
        (fn [prev]
          (println "This will never complete!")
          (+ 1 prev))
        (task/run
          (+ 3 @nothing))))))
  ;; end::whoops[]

(task/complete! whoops)

@(task/for
   [_ multiple-executors
    _ for-1
    _ sequence1
    _ rebind
    _ whoops
    _ traverse1]
   (System/exit 0))

