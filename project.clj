(defproject com.github.ane/task "0.1.0-SNAPSHOT"
  :description "Task is a Clojure library for asynchronous
  computation. It provides functional primitives that
  makes concurrent programming accessible and powerful."
  :url "http://example.com/FIXME"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :source-paths ["src/main/clj"]
  :test-paths ["src/test/clj"]
  :resource-pahts ["src/main/resources"]
  :plugins [[test2junit "1.1.2"]
            [lein-codox "0.10.3"]]
  :codox {:output-path "docs"
          :themes [:default :kingfisher]
          :metadata {:doc/format :markdown}
          :source-uri "https://github.com/ane/task/blob/master/{filepath}#L{line}"}
  :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit"))
