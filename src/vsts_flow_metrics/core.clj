(ns vsts-flow-metrics.core
  (:require [vsts-flow-metrics.work-items :as work-items]
            [vsts-flow-metrics.storage :as storage]
            [vsts-flow-metrics.api :as api]
            [vsts-flow-metrics.config :as cfg]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.format :as f]))


(defn- map-values
  [m f]
  (reduce-kv (fn [m k v]
               (assoc m k (f v))) {} m))


(defn cycle-time-in-days
  [state-intervals]
  (map-values
   (fn [intervals]
     (let [cycle-time (work-items/dev-cycle-time intervals)]
       (if-let [cycle-time-hours (:hours cycle-time)]
         (/ cycle-time-hours 24.0))))
   state-intervals))
