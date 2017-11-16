(ns task.core-test
  (:require [clojure.test :refer :all]
            [task.core :refer :all])
  (:import java.util.concurrent.ExecutionException))

; testing I didn't screw up the Java functional crap
(deftest supplier-tests
  (testing "supplier works"
    (is (= 1 (.get (supplier (fn [] 1)))))))

(deftest function-tests
  (testing "application"
    (is (= 16 (.apply (function #(* 2 %1)) 8)))))

(deftest composition 
  (testing "composition"
    (is (= 2 (let [fst (function #(* 1 %))
                   snd (function #(* -1 %))]
               (.apply (.compose fst snd) -2))))))

(deftest chaining
  (testing "chaining"
   (is (= -2 (let [fst (function #(* 1 %1))
                   snd (function #(* -1 %1))]
              (.apply (.andThen fst snd) 2))))))

;; basic stuff

(deftest promotion
  (testing "values can be promoted"
    (is (= 'foo @(now 'foo)))))

(deftest run-test
  (testing "run works"
    (is (= 123 @(run 123)))))

(deftest then-tests
  (testing "basic application"
    (is (= 2 @(then inc (now 1)))))

  (testing "comp works"
    (is (= 3 @(then (comp inc inc) (now 1))))))

(deftest compose-tests
  (testing "basic application"
    (is (= 2 @(compose #(run (inc %)) (now 1))))))

(deftest for-tests
  (testing "for works"
    (is (= 11 @(for [a (future 1)
                     b (now 3)
                     c (run 7)]
                 (+ a b c))))))

(deftest sequence-tests
  (testing "sequence works"
    (is (= [1 2 3] @(sequence [(run 1) (now 2) (future 3)])))))

(deftest complete-tests
  (testing "complete! completes"
    (let [task (run (Thread/sleep 2000) 9)]
      (complete! task "bla")
      (is (= "bla" @task))))
  (testing "complete! doesn't override a ready task"
    (let [task (now 1)]
      (complete! task "fff")
      (is (= 1 @task))))
  (testing "force! forces a value"
    (let [task (now 'foo)]
      (force! task 'bar)
      (is (= 'bar @task)))))

(deftest failure-tests
  (testing "get on nonsense throws ExecutionException "
    (is (thrown-with-msg? ExecutionException #"ArithmeticException" @(run (/ 1 0)))))
  (testing "failed? works"
    (is (failed? (failed (IllegalStateException. "asdf")))))
  (testing "failure works"
    (is (instance? RuntimeException (failure (failed (RuntimeException. "foo")))))
    (is (= nil (failure (now 1)))))
  (testing "fail! works"
    (let [ff (failed (RuntimeException. "bork"))]
      (fail! ff (IllegalStateException. "bad state"))
      (is (instance? IllegalStateException (failure ff))))))

(deftest misc-tests
  (testing "now is always done"
    (is (done? (now 1))))
  (testing "void is void"
    (is (not (done? (void)))))
  (testing "else works"
    (is (= 'foo (else (void) 'foo)))
    (is (= 'bar (else (run (Thread/sleep 1000) 'baz) 'bar)))))

(deftest recover-tests
  (testing "recover works"
    (is (= 'bla
           @(recover (failed (IllegalArgumentException.))
                     ; let's be picky...
                     (fn [x] (when (= IllegalArgumentException (class x))
                               'bla)))))))
