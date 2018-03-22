(ns vsts-flow-metrics.storage
  (:require [vsts-flow-metrics.api :as api]
            [vsts-flow-metrics.config :as cfg]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cheshire.core :as json]))


(def file-format (f/formatters :date-hour-minute))

(def as-of-format (f/formatter "MM/dd/yyyy"))

(defn save-work-item-state-changes
  [work-item-state-changes output-path]
  (spit output-path (json/generate-string work-item-state-changes {:pretty true}))
  (println "Saved work item state changes in " (.getAbsolutePath (io/file output-path)))
  (.getAbsolutePath (io/file output-path)))

(defn- load-work-item-state-changes [path]
  (println "Loading work item state changes from " (.getAbsolutePath (io/file path)))
  (json/parse-string (slurp (io/file path)) true))

(defn load-state-changes-from-cache [json-file]
  (if (.isAbsolute (io/file json-file))
    (load-work-item-state-changes json-file)
    (load-work-item-state-changes (str "cache/" json-file))))

(defn work-item-state-changes
  "Gets all state transition changes to a work item. NOTE: Makes one VSTS API call per work item."
  [wiql-path project]
  (let [wiql (slurp (io/file wiql-path))
        work-items (api/query-work-items (cfg/vsts-instance) project wiql)
        ids (map :id (:workItems work-items))
        changes (map #(api/get-work-item-state-changes (cfg/vsts-instance) %) ids)]
    (zipmap ids changes)))

(defn cache-changes [wiql-path project]
  (let [wiql-path-base (.getName (io/file wiql-path))
        timestamp (t/now)]
    (save-work-item-state-changes
     (work-item-state-changes wiql-path project)
     (str "cache/" (f/unparse file-format timestamp) "-" wiql-path-base ".json"))))
