(ns vsts-flow-metrics.storage
  (:require [vsts-flow-metrics.api :as api]
            [vsts-flow-metrics.config :as cfg]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cheshire.core :as json]
            [cheshire.generate :refer [add-encoder encode-str remove-encoder]]
            [vsts-flow-metrics.pull-requests :as pull-requests]))


(def file-format (f/formatters :date-hour-minute))
(def as-of-format (f/formatter "MM/dd/yyyy"))

(defn generate-wiql-as-of [wiql-path interesting-time]
   (format (slurp wiql-path) (f/unparse as-of-format interesting-time)))


;; First, add a custom encoder for a class:
(add-encoder org.joda.time.DateTime
             (fn [c jsonGenerator]
               (.writeString jsonGenerator (f/unparse (f/formatters :date-time) c))))

;; There are also helpers for common encoding actions:
(add-encoder java.net.URL encode-str)

(defn save-work-item-state-changes
  [work-item-state-changes output-path]
  (spit output-path (json/generate-string work-item-state-changes {:pretty true}))
  (.getAbsolutePath (io/file output-path)))

(defn- load-work-item-state-changes [path]
  (json/parse-string (slurp (io/file path)) true))

(defn load-state-changes-from-cache [json-file]
  (load-work-item-state-changes json-file))

(defn work-item-state-changes
  "Gets all state transition changes to a work item. NOTE: Makes one VSTS API call per work item."
  [wiql-path-or-raw project]
  (let [wiql-path-or-raw-file (io/file wiql-path-or-raw)
        wiql (cond
               (.exists wiql-path-or-raw-file)
               (slurp wiql-path-or-raw-file)

               (clojure.string/starts-with? (clojure.string/lower-case
                                             (clojure.string/trim wiql-path-or-raw))
                                            "select")
               wiql-path-or-raw

               :else
               (throw (RuntimeException. (str "Invalid wiql-path or raw wiql: " wiql-path-or-raw))))
        query-result (api/query-work-items (cfg/vsts-instance) project wiql)]
    (if (= (:queryResultType query-result) "workItemLink")
      ;; we only deal with "System.LinkTypes.Hierarchy-Forward" (parent/child) for now
      (let [links (remove #(not= (:rel %) "System.LinkTypes.Hierarchy-Forward")
                          (:workItemRelations query-result))
            process-link
            (fn [acc link]
              (let [source-id (keyword (str (:id (:source link))))
                    target-id (keyword (str (:id (:target link))))]
                (-> acc
                    (update  source-id
                             (fn [old-source]
                               (if old-source
                                 old-source
                                 {:changes (api/get-work-item-state-changes
                                            (cfg/vsts-instance)
                                            (name source-id))
                                  :children {}})))
                    (update-in [source-id :children]
                               #(assoc % target-id (api/get-work-item-state-changes
                                                    (cfg/vsts-instance)
                                                    (name target-id)))))))]
        (reduce process-link {} links))



      ; flat list of work items
      (let  [ids (map :id (:workItems query-result))
             changes (map #(api/get-work-item-state-changes (cfg/vsts-instance) %) ids)]
        (zipmap (map (comp keyword str) ids) changes)))))

(defn cache-changes [wiql-path]
  (let [wiql-path-base (.getName (io/file wiql-path))
        timestamp (t/now)]
    (save-work-item-state-changes
     (work-item-state-changes wiql-path (:project (cfg/config)))
     (str "cache/" (f/unparse file-format timestamp) "-" wiql-path-base ".json"))))


(defn work-items-as-of [wiql-path interesting-times]
  (->> interesting-times

       (map (fn [time]
              (let [work-items-ids-result (api/query-work-items
                                           (cfg/vsts-instance)
                                           (:project (cfg/config))
                                           (generate-wiql-as-of wiql-path time))
                    work-item-ids (map :id (:workItems work-items-ids-result))
                    work-items (api/get-work-items (cfg/vsts-instance)
                                                   work-item-ids
                                                   time)]
                [time (:value work-items)])))

       (into {})))

(defn pull-requests
  [config]
  (let [instance (cfg/vsts-instance config)
        repo (api/get-repository-by-name instance
                                  (:project config)
                                  (:pull-requests (:repository config)))
        team-name  (:team-name (:pull-requests config))
        active-prs (api/get-pull-requests instance
                                      (:project config)
                                      repo
                                      "active"
                                      team-name)
        completed-prs (api/get-pull-requests instance
                                      (:project config)
                                      repo
                                      "completed"
                                      team-name)
        abandoned-prs (api/get-pull-requests instance
                                      (:project config)
                                      repo
                                      "abandoned"
                                      team-name)

        by-id (fn [prs]  (into {} (map (fn [pull-req]
                                        [(:pullRequestId pull-req) pull-req])) prs))]
    {:as-of     (t/now)
     :active    (by-id active-prs)
     :completed (by-id completed-prs)
     :abandoned (by-id abandoned-prs)}))


(defn cache-pull-requests
  [config]
  (let [prs (pull-requests config)
        as-of-name  (f/unparse file-format (:as-of prs))
        output-path (str "cache/" as-of-name "-pull-requests.json")]
    (spit output-path (json/generate-string
                       (assoc prs :as-of (f/unparse (f/formatter :date-time) (:as-of prs)))
                       {:pretty true}))
    (.getAbsolutePath (io/file output-path))))

(defn cache-pull-requests-closed-after
  [config closed-after-date]
  (let [prs (pull-requests config)
        closed-after? (fn [pull-req]
                        (if-let [closed-date (:closedDate (second pull-req))]
                          (t/after? (f/parse (f/formatter :date-time) closed-date)
                                    closed-after-date)))
        filtered-prs {:active (:active prs)
                      :completed (into {} (filter closed-after? (:completed prs)))
                      :abandoned (into {} (filter closed-after? (:abandoned prs)))}

        as-of-name  (f/unparse file-format (:as-of prs))
        closed-after-date-s  (f/unparse file-format closed-after-date)
        output-path (str "cache/" as-of-name "-pull-requests-closed-after-" closed-after-date-s ".json")]
    (spit output-path (json/generate-string
                       (assoc filtered-prs :as-of
                              (f/unparse (f/formatter :date-time)
                                         (:as-of prs)))
                       {:pretty true}))
    (.getAbsolutePath (io/file output-path))))

(defn load-pull-requests-from-cache [json-file]
  (let [raw-prs (json/parse-string (slurp (io/file json-file)) true)

        normalize (fn [pull-reqs] (zipmap (keys pull-reqs)
                                         (map pull-requests/normalize-pull-request (vals pull-reqs))))]
    (-> raw-prs
        (assoc :as-of (f/parse (f/formatter :date-time) (:as-of raw-prs)))
        (update-in [:active] normalize)
        (update-in [:completed] normalize)
        (update-in [:abandoned] normalize))))
