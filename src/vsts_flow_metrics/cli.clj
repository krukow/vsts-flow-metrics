(ns vsts-flow-metrics.cli
  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [vsts-flow-metrics.core :as core]
            [vsts-flow-metrics.storage :as storage]
            [vsts-flow-metrics.config :as cfg]
            [clojure.string :as string])
  (:gen-class :main true))



(defn- dump-usage
  [cli-summary]
  (binding [*out* *err*]
    (println "USAGE:")
    (println cli-summary)
    (println "\nTools:
    cache-changes <wiql-path> <Project> Queries work items specified in file <wiql-path> and save in cache folder. <Project> is the VSTS instance project to query, e.g. Mobile-Center.
    ")))

(def cli-options
  [(comment ["-w" "--weekly-items WORK-ITEMS" "Commma separated list of work-item ids"
             :parse-fn (fn [csl]
                         (map #(Integer/parseInt %)
                              (clojure.string/split csl #",")))])])


(defn- check-file-exists!
  [file]
  (when-not (and file (.exists file))
    (println "You must specify an existing file:" (str file))
    (System/exit 1)))


(defn cache-changes
  [options args]
  (let [[wiql-file-path project] args]
    (when-not (and wiql-file-path (.exists (io/file wiql-file-path)))
      (println "WIQL File does not exist:" wiql-file-path)
      (throw (RuntimeException. (str "File does not exist:" wiql-file-path))))
    (when-not (.isDirectory (io/file "cache"))
      (println "Please create directory: cache")
      (throw (RuntimeException. (str "No cache directory invalid"))))
    (when (string/blank? project)
      (println "Please specify a VSTS project name in the VSTS instance")
      (throw (RuntimeException. (str "No project name specified"))))

    (println "Query and cache WIQL" wiql-file-path)
    (storage/cache-changes "wiql/closed-features-30d.wiql" project)))

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


    (when (clojure.string/blank? (cfg/access-token))
      (println "You must specify environment variable:" "VSTS_ACCESS_TOKEN")
      (System/exit 1))

    (when (clojure.string/blank? (:name (cfg/vsts-instance)))
      (println "You must specify environment variable:" "VSTS_INSTANCE" " (for example export VSTS_INSTANCE=msmobilecenter.visualstudio.com)")
      (System/exit 1))


    ;; Launch selected tool
    (try
      (case (first arguments)
        "cache-changes" (cache-changes options (rest arguments))
        (binding [*out* *err*]
          (println "** No such tool:" (first arguments))
          (dump-usage summary)
          (System/exit 1)))
      (catch Exception e
        (do
          (println e "Uncaught throwable")
          (if (System/getenv "DEBUG") (.printStackTrace e))
          (System/exit 27))))
    (System/exit 0)))
