(ns examples.clj.task.http
  (:require [org.httpkit.client :as http]
            [cheshire.core :as cheshire]
            [task.core :as task]
            [clojure.string :as str]))


(def request
  (task/then
   (fn [data] (-> data
                  :body
                  (cheshire/parse-string true)
                  :title
                  str/upper-case))
   (http/get "http://jsonplaceholder.typicode.com/posts/3")))

