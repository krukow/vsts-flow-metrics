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
  (let [token (environment-config "VSTS_ACCESS_TOKEN")]
    (when (nil? token)
      (RuntimeException. "Please ensure environment variable VSTS_ACCESS_TOKEN is set"))
    token))

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
    :field :System.State ;;or :System.BoardColumn
    :chart {:title "Cycle time control chart"
            :width 1440
            :height 900
            :x-axis {:title "Work items"}
            :y-axis {:title "Cycle time" :decimal-pattern "## days"}
            :theme :xchart}}


   :time-in-state
   {:field :System.State ;; or :System.BoardColumn
    :chart {:title "Time spent in state"
            :overlap? true
            :render-style :bar
            ;; this is optimized for features, customize to your team's needs!
            :remove-states ["Closed" "Cancelled" "Needs More Info"
                            "Approved" "In Spec" "Ready for Triage"
                            "Ready For Work"] ;; Keep real states remove board states for features
            :series-order ["New" "Need More Info" "Ready For Triage" "Ready for Work" "Active" "Blocked"]
            :width 1440
            :height 900
            :x-axis {:title "Work items"}
            :y-axis {:title "Time in state"
                     :decimal-pattern "##.## days"}

            :theme :xchart}}


   :flow-efficiency
   {:active-states ["Active"]
    :blocked-states ["Blocked" "In Review"]
    :field :System.State ;;or :System.BoardColumn
    :chart {:title "Flow efficiency"
            :overlap? true
            :render-style :bar
            :width 1440
            :height 900
            :x-axis {:title "Work items"}
            :y-axis {:title "Flow efficiency"
                     :decimal-pattern "#.## %"}
            :theme :xchart}}

   :responsiveness
   {:from-state "Ready for Work"
    :to-state "Active"
    :field :System.State ;;or :System.BoardColumn
    :chart {:title "Responsiveness"
            :category-title "Responsiveness"
            :overlap? true
            :render-style :bar
            :x-axis {:title "Work items"}
            :y-axis {:title "Responsiveness in days"
                     :decimal-pattern "## days"}
            :theme :xchart}}


   :lead-time-distribution
   {:from-state "Active"
    :to-state   "Closed"
    :field :System.State ;; or :System.BoardColumn
    :chart {:title "Lead Time Distribution"
            :category-title "Backlog items"
            :x-axis {:title "Lead time in days"}
            :y-axis {:title "Number of items"
                     :decimal-pattern "##"}
            :theme :xchart}}

   :historic-queues
   {:ago 30 ;; days
    :step 3 ;; day increments
    :field :System.State ;; or :System.BoardColumn
    :chart {:title "Queue by state"
            :remove-states ["Closed" "Cancelled"]
            :series-order nil
            :stacked? true
            :render-style :bar
            :x-axis {:title "Time (ago)"}
            :y-axis {:title "Number in state"
                     :decimal-pattern "##"}
            :theme :xchart}}

   })

(defn config
  []
  (deep-merge default-config (load-config-from-file (config-file))))

(defn vsts-field [key]
  (let [field (get-in (config) [key :field])]
    (keyword field)))
