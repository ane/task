(defproject com.github.ane/task "0.1.0"
  :description "Task is a Clojure library for asynchronous
  computation. It provides functional primitives that
  makes concurrent programming accessible and powerful."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :source-paths ["src/main/clj"]
  :test-paths ["src/test/clj"]
  :resource-pahts ["src/main/resources"]
  :plugins [[test2junit "1.1.2"]
            [lein-codox "0.10.3"]]
  :codox {:output-path "docs"
          :themes [:default :kingfisher]
          :source-uri "https://github.com/ane/task/blob/master/{filepath}#{line}"}
  :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit"))
