(ns vsts-flow-metrics.cli
  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [vsts-flow-metrics.core :as core]
            [vsts-flow-metrics.storage :as storage]
            [vsts-flow-metrics.charts :as charts]
            [vsts-flow-metrics.config :as cfg]
            [clojure.string :as string])
  (:gen-class :main true))



(defn- dump-usage
  [cli-summary]
  (binding [*out* *err*]
    (println "USAGE:")
    (println cli-summary)
    (println "\nTools:
    show-config                         - Prints current configuration in JSON format (overrides specified with VSTS_FLOW_CONFIG=my-overrides.json).
    cache-work-item-changes <wiql-path> - Queries work items specified in .wiql file: <wiql-path>. Saves results in a cache folder. Note: a VSTS project must be specified in the config.
    cycle-time <cached-changes>         - Prints cycle times for a set of work-items cached in path <cached-changes>. Use the --chart option to save a chart.
    time-in-state <cached-changes>      - Prints time in states for a set of work-items cached in path <cached-changes>. Use the --chart option to save a chart.

    ")))

(def cli-options
  [["-h" "--help"  "Prints usage"]
   ["-c" "--chart FILENAME" "Saves a chart instead of printing data to stdout (.svg, .png, ...). Note that .pdf generation is very slow for some reason for .svg is preferred for vector graphs."
    :parse-fn (fn [fn]
                (when (nil? fn)
                  (println "Chart filename must be specified")
                  (System/exit 1))
                (io/file fn))]])

(defn- check-file-exists!
  [file]
  (when-not (and file (.exists file))
    (println "You must specify an existing file:" (str file))
    (System/exit 1)))


(defn print-result
  [res]
  (println (json/generate-string res {:pretty true})))

(defn show-config
  []
  (println (json/generate-string (cfg/config) {:pretty true})))

(defn cache-work-item-changes
  [_ args]
  (let [[wiql-file-path] args]
    (when (nil? wiql-file-path)
      (println "You must specify a path to a WIQL file.")
      (System/exit 1))
    (when-not (.exists (io/file wiql-file-path))
      (println "WIQL File does not exist:" wiql-file-path)
      (throw (RuntimeException. (str "File does not exist:" wiql-file-path))))
    (when-not (.isDirectory (io/file "cache"))
      (println "Please create a cache directory named: ./cache")
      (throw (RuntimeException. (str "No cache directory: ./cache exists"))))

    (println "Querying and caching changes to work items in " wiql-file-path)
    (println "Note: this may take some time depending on the number of work items in the result..." )
    (storage/cache-changes wiql-file-path (:project (cfg/config)) )))

(defn cycle-time [options args]
  (let [[cached-file-path] args]
    (when (nil? cached-file-path)
      (println "You must specify a path to a cached changes file.")
      (System/exit 1))
    (when-not (.exists (io/file cached-file-path))
      (println "File does not exist:" cached-file-path)
      (throw (RuntimeException. (str "File does not exist:" cached-file-path))))

    (let [cycle-times (-> cached-file-path
                          storage/load-state-changes-from-cache
                          core/intervals-in-state
                          core/cycle-times)]
      (if (:chart options)
        (charts/view-cycle-time cycle-times
                                (charts/default-chart-options :cycle-time)
                                (:chart options))
        (print-result cycle-times)))))

(defn time-in-state [options args]
  (let [[cached-file-path] args]
    (when (nil? cached-file-path)
      (println "You must specify a path to a cached changes file.")
      (System/exit 1))
    (when-not (.exists (io/file cached-file-path))
      (println "File does not exist:" cached-file-path)
      (throw (RuntimeException. (str "File does not exist:" cached-file-path))))

    (let [times-in-states (-> cached-file-path
                              storage/load-state-changes-from-cache
                              core/intervals-in-state
                              core/days-spent-in-state)]
      (if (:chart options)
        (charts/view-time-in-state times-in-states
                                   (charts/default-chart-options :time-in-state)
                                   (:chart options))
        (print-result times-in-states)))))

(defn -main
  [& args]
  (let [{:keys [options arguments summary errors]}
        (cli/parse-opts args cli-options)]
    (cond
      errors (binding [*out* *err*]
               (println "** Failed to parse command line options:" errors)
               (dump-usage summary)
               (System/exit 1))
      (:help options) (do
                        (dump-usage summary)
                        (System/exit 0)))


    (when-not (or (string/blank? (first arguments))
                  (= "show-config" (first arguments)))
      (when (string/blank? (cfg/access-token))
        (println "You must specify environment variable:" "VSTS_ACCESS_TOKEN")
        (System/exit 1))

      (when (string/blank? (:name (cfg/vsts-instance)))
        (println "You must specify environment variable:" "VSTS_INSTANCE" " (for example export VSTS_INSTANCE=msmobilecenter.visualstudio.com)")
        (System/exit 1)))

    ;; Launch selected tool
    (try
      (case (first arguments)
        "show-config" (show-config)
        "cache-work-item-changes" (cache-work-item-changes options (rest arguments))
        "cycle-time" (cycle-time options (rest arguments))
        "time-in-state" (time-in-state options (rest arguments))
        (binding [*out* *err*]
          (when (first arguments)
            (println "** No such tool: " (first arguments)))
          (dump-usage summary)
          (System/exit 1)))
      (catch Exception e
        (do
          (println e "Uncaught throwable")
          (if (System/getenv "DEBUG") (.printStackTrace e))
          (System/exit 27))))
    (System/exit 0)))
