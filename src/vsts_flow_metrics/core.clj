(ns vsts-flow-metrics.core
  (:require [vsts-flow-metrics.work-items :as work-items]
            [vsts-flow-metrics.storage :as storage]
            [vsts-flow-metrics.api :as api]
            [vsts-flow-metrics.config :as cfg]
            [vsts-flow-metrics.charts :as charts]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(defn- map-values
  [f m]
  (reduce-kv (fn [m k v] (assoc m k (f v))) {} m))


(defn intervals-in-state
  [work-items-changes]
  (map-values work-items/intervals-in-state work-items-changes))

(defn cycle-times
  "Computes cycle time in days (defined as hours / 24.0) for a set of work items with state intervals.
  Cycle time is time from first in `from-state` to last in `to-state`. If called without from-state and to-state,
  defaults to the configuration :cycle-time :from-state / :to-state."
  ([state-intervals]
   (cycle-times state-intervals (:cycle-time (cfg/config))))
  ([state-intervals {:keys [from-state to-state]}]
   (map-values
    (fn [intervals]
      (let [cycle-time (work-items/cycle-time intervals from-state to-state)]
        (if-let [cycle-time-hours (:hours cycle-time)]
          (/ cycle-time-hours 24.0))))
    state-intervals)))

(defn days-spent-in-state
  [state-intervals]
  (map-values work-items/days-spent-in-states state-intervals))

(defn flow-efficiency
  ([state-intervals]
   (flow-efficiency state-intervals (:flow-efficiency (cfg/config))))
  ([state-intervals {:keys [active-states blocked-states]}]
   (map-values
    (fn [time-spent-data]
      (work-items/flow-efficiency time-spent-data active-states blocked-states))
    state-intervals)))
