(ns task.core-test
  (:require [clojure.test :refer :all]
            [task.core :refer :all]))

(deftest supplier-tests
  (testing "supplier works"
    (is (= 1 (.get (supplier (fn [] 1)))))))

(deftest function-tests
  (testing "application"
    (is (= 16 (.apply (function #(* 2 %1)) 8)))))

(deftest composition )
  (testing "composition"
    (is (= 2 (let [fst (function #(* 1 %))
                   snd (function #(* -1 %))]
               (.apply (.compose fst snd) 2))))))

(deftest chaining
  (testing "chaining"
   (is (= -2 (let [fst (function #(* 1 %1))
                   snd (function #(* -1 %1))]
              (.apply (.andThen fst snd) 2))))))
