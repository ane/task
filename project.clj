(defproject com.github.ane/task "0.4.0-SNAPSHOT"
  :description "Task is a Clojure library for asynchronous
  computation. It provides functional primitives that
  makes concurrent programming accessible and powerful."
  :url "http://ane.github.com/task"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.github.ane/codox-kingfisher-theme "0.1.0"]]
  :profiles {:dev {:dependencies [[http-kit "2.2.0"]
                                  [cheshire "5.8.0"]]}}
  :source-paths ["src/main/clj"]
  :test-paths ["src/test/clj"]
  :deploy-repositories [["releases" :clojars]]
  :resource-paths ["src/main/resources"]
  :plugins [[test2junit "1.1.2"]
            [lein-codox "0.10.3"]
            [lein-exec "0.3.7"]]
  :codox {:output-path "docs/api"
          :themes [:default :kingfisher]
          :metadata {:doc/format :markdown}
          :source-uri "https://github.com/ane/task/blob/master/{filepath}#L{line}"}
  :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit"))
