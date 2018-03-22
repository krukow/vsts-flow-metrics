(ns vsts-flow-metrics.config
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clj-http.conn-mgr :as conn-mgr]
            [cheshire.core :as json]))

(defn- environment-config
  [name]
  (let [env (System/getenv name)]
    (if (string/blank? env)
      nil
      env)))


(defn- load-config-from-file
  [path]
  (cond
    (nil? path) {}
    (not (.exists (io/file path))) (throw (RuntimeException. (str "VSTS_FLOW_CONFIG File: " path " set, but does not exist.")))
    :else (-> (io/file path)
              slurp
              (json/parse-string true))))

(defn config-file
  []
  (environment-config "VSTS_FLOW_CONFIG"))

(defn access-token
  []
  (environment-config "VSTS_ACCESS_TOKEN"))

(def http-options
  {:basic-auth (str ":" (access-token))
   :socket-timeout 30000  ;; in milliseconds
   :conn-timeout 10000
   :connection-manager (conn-mgr/make-reusable-conn-manager {})
   :accept :json})

(defn vsts-instance
  []
  {:name (environment-config "VSTS_INSTANCE")
   :http-options http-options})


(defn deep-merge
  "Merges maps of similar shapes (used for default overriding config files).
  The default must have all the keys present."
  [default overrides]
  (letfn [(deep-merge-rec [a b]
                           (if (map? a)
                               (merge-with deep-merge-rec a b)
                               b))]
    (reduce deep-merge-rec nil (list default overrides))))


(def default-config
  {:project (or (environment-config "VSTS_PROJECT") "Mobile-Center") ;; replace this
   :cycle-time
   {:from-state "Active"
    :to-state "Closed"
    :chart {:title "Cycle time control chart"
            :x-axis {:title "Work items"}
            :y-axis {:title "Cycle time" :decimal-pattern "## days"}
            :width 1440
            :height 900
            :theme :xchart}}})

(defn config
  []
  (deep-merge default-config (load-config-from-file (config-file))))
