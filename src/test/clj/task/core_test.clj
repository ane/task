(ns task.core-test
  (:require [clojure.test :refer :all]
            [task.core :as task])
  (:import java.util.concurrent.ExecutionException))

; testing I didn't screw up the Java functional crap
(deftest supplier-tests
  (testing "supplier works"
    (is (= 1 (.get (task/fn->Supplier (fn [] 1)))))))

(deftest function-tests
  (testing "application"
    (is (= 16 (.apply (task/fn->Function #(* 2 %1)) 8)))))

(deftest composition 
  (testing "composition"
    (is (= 2 (let [fst (task/fn->Function #(* 1 %))
                   snd (task/fn->Function #(* -1 %))]
               (.apply (.compose fst snd) -2))))))

(deftest chaining
  (testing "chaining"
   (is (= -2 (let [fst (task/fn->Function #(* 1 %1))
                   snd (task/fn->Function #(* -1 %1))]
              (.apply (.andThen fst snd) 2))))))

;; basic stuff

(deftest promotion
  (testing "values can be promoted"
    (is (= 'foo @(task/now 'foo)))))

(deftest run-test
  (testing "run works"
    (is (= 123 @(task/run 123)))))

(deftest then-tests
  (testing "basic application"
    (is (= 2 @(task/then inc (task/now 1)))))

  (testing "comp works"
    (is (= 3 @(task/then (comp inc inc) (task/now 1))))))

(deftest compose-tests
  (testing "basic application"
    (is (= 2 @(task/compose #(task/run (inc %)) (task/now 1))))))

(deftest for-tests
  (testing "for works"
    (is (= 11 @(task/for [a (future 1)
                          b (task/now 3)
                          c (task/run 7)]
                 (+ a b c))))))

(deftest sequence-tests
  (testing "sequence works"
    (is (= [1 2 3] @(task/sequence [(task/run 1) (task/now 2) (future 3)])))))

(deftest complete-tests
  (testing "complete! completes"
    (let [task (task/run (Thread/sleep 2000) 9)]
      (task/complete! task "bla")
      (is (= "bla" @task))))
  (testing "complete! doesn't override a ready task"
    (let [task (task/now 1)]
      (task/complete! task "fff")
      (is (= 1 @task))))
  (testing "force! forces a value"
    (let [task (task/now 'foo)]
      (task/force! task 'bar)
      (is (= 'bar @task)))))

(deftest failure-tests
  (testing "get on nonsense throws ExecutionException "
    (is (thrown-with-msg? ExecutionException #"ArithmeticException" @(task/run (/ 1 0)))))
  (testing "failed? works"
    (is (task/failed? (task/failed (IllegalStateException. "asdf")))))
  (testing "failure works"
    (is (instance? RuntimeException (task/failure (task/failed (RuntimeException. "foo")))))
    (is (= nil (task/failure (task/now 1)))))
  (testing "fail! works"
    (let [ff (task/failed (RuntimeException. "bork"))]
      (task/fail! ff (IllegalStateException. "bad state"))
      (is (instance? IllegalStateException (task/failure ff))))))

(deftest misc-tests
  (testing "now is always done"
    (is (task/done? (task/now 1))))
  (testing "void is void"
    (is (not (task/done? (task/void)))))
  (testing "else works"
    (is (= 'foo (task/else (task/void) 'foo)))
    (is (= 'bar (task/else (task/run (Thread/sleep 1000) 'baz) 'bar)))))

(deftest recover-tests
  (testing "recover works"
    (is (= 'bla
           @(task/recover (task/failed (IllegalArgumentException.)) ; let's be picky...
                          (fn [x] (when (= IllegalArgumentException (class x))
                                    'bla)))))))

(deftest traverse-tests
  (testing "traverse works"
    (is (= [2 4 6 8 10]
           @(task/traverse [1 2 3 4 5] (fn [x] (task/run (* 2 x))))))))
