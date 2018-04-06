(ns vsts-flow-metrics.core
  (:require [vsts-flow-metrics.work-items :as work-items]
            [vsts-flow-metrics.storage :as storage]
            [vsts-flow-metrics.api :as api]
            [vsts-flow-metrics.config :as cfg]
            [vsts-flow-metrics.charts :as charts]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(defn map-values
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
  ([state-intervals {:keys [from-state to-state field]}]
   (map-values
    (fn [intervals]
      (let [cycle-time (work-items/cycle-time (get intervals (keyword field))
                                              from-state to-state)]
        (if-let [cycle-time-hours (:hours cycle-time)]
          (/ cycle-time-hours 24.0))))
    state-intervals)))

(defn days-spent-in-state
  [state-intervals]
  (let [field (cfg/vsts-field :time-in-state)
        field-state-intervals (map-values #(get % field) state-intervals)]
    (map-values work-items/days-spent-in-states field-state-intervals)))


(defn flow-efficiency
  ([state-intervals]
   (flow-efficiency state-intervals (:flow-efficiency (cfg/config))))
  ([state-intervals {:keys [active-states blocked-states field]}]
   (map-values
    (fn [time-spent-data]
      (work-items/flow-efficiency (get time-spent-data (keyword field))
                                  active-states blocked-states))
    state-intervals)))

(defn responsiveness
  "Computes responsiveness from `from-state` to `to-state`"
  ([state-intervals]
   (responsiveness state-intervals (:responsiveness (cfg/config))))
  ([state-intervals {:keys [from-state to-state field]}]
   (map-values
    (fn [time-spent-data] (work-items/responsiveness (get time-spent-data (keyword field))
                                                    from-state to-state))
    state-intervals)))

(defn lead-time-distribution
  ([state-intervals]
   (lead-time-distribution state-intervals (:lead-time-distribution (cfg/config))))
  ([state-intervals options]
   (let [cycle-times (cycle-times state-intervals options)
         lead-time-dist (frequencies (map #(Math/round %) (remove nil? (vals cycle-times))))]
     lead-time-dist)))

(defn work-items-state-distribution
  [work-items-as-of]
  (let [field (cfg/vsts-field :historic-queues)]
    (into {}
          (map (fn [[k v]]
                 (let [states (work-items/work-items-states v field)]
                   [k (frequencies (remove nil? states))]))
               work-items-as-of))))

(defn interesting-times
  [cfg]
  (let [ago (:ago cfg)
        step (:step cfg)]
    (map (fn [ago] (t/minus (t/now) (t/days ago))) (range 0 (inc ago) step))))
