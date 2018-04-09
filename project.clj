(defproject vsts-flow-metrics "1.0.5-SNAPSHOT"
  :description "Tool for visualizing flow metrics from Visual Studio Team Services data"
  :url "https://github.com/krukow/vsts-flow-metrics"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :uberjar-name "flow-metrics.jar"
  :main vsts-flow-metrics.cli
  :profiles {:uberjar {:aot :all}}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-http "3.8.0"]
                 [cheshire "5.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [com.hypirion/clj-xchart "0.2.0"]
                 [incanter "1.9.2"]
                 [clj-time "0.13.0"]]

  :plugins [[cider/cider-nrepl "0.16.0"]])
