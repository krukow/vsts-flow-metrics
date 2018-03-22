(ns vsts-flow-metrics.api
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-http.client :as client]
            [vsts-flow-metrics.config :as cfg]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cheshire.core :as json]))


(defn- work-items-url
  ([instance ids]
   (str "https://" (:name instance)
        "/DefaultCollection/_apis/wit/workitems?api-version=3.0-preview&ids=" (string/join "," ids)
        "&$expand=relations&ErrorPolicy=omit"))
  ([instance ids as-of]
   (str "https://" (:name instance)
        "/DefaultCollection/_apis/wit/workitems?api-version=3.0-preview&ids=" (string/join "," ids)
        "&asOf=" (f/unparse (f/formatter :date-time) as-of)
        "&$expand=relations&ErrorPolicy=omit")))

(defn- work-item-updates-url [instance id]
  (str "https://" (:name instance)
       "/_apis/wit/workitems/" id "/updates?api-version=3.0-preview"))


(defn- wiql-url [instance project]
  (str "https://" (:name instance)
       "/DefaultCollection/" project "/_apis/wit/wiql?api-version=3.0-preview"))


(defn get-work-items
  ([instance ids]
   (let [response (client/get (work-items-url instance ids)
                              (:http-options instance))]
     (json/parse-string (:body response) true)))
  ([instance ids as-of]
   (let [response (client/get (work-items-url instance ids as-of)
                              (:http-options instance))]
     (json/parse-string (:body response) true))))

(defn query-work-items
  [instance project query]
  (let [response
        (client/post (wiql-url instance project)
                     (merge
                      (:http-options instance)
                      {:form-params {:query query} :content-type :json}))]
    (json/parse-string (:body response) true)))

(defn get-work-item-updates [instance id]
  (let [response (client/get (work-item-updates-url instance id)
                             (:http-options instance))]
    (json/parse-string (:body response) true)))

(defn get-work-item-state-changes [instance id]
  (let [response (client/get (work-item-updates-url instance id)
                             (merge (:http-options instance)
                                    {:throw-exceptions false}))
        item-updates (json/parse-string (:body response) true)]
    (filter
     (fn [{fields :fields}]
       (or (:System.State fields)
           (:System.BoardColumn fields)))
     (:value item-updates))))
