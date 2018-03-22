(ns vsts-flow-metrics.charts
  (:require [com.hypirion.clj-xchart :as c]
            [vsts-flow-metrics.core :as core]
            [incanter.stats :as istats]))


(def default-percentiles      [0.25              0.5      0.80              0.90])
(def default-percentile-names ["25th percentile" "median" "80th percentile" "90th percentile"])

(defn- percentiles-for-graph
  ([item-names [pct25 median pct80 pct90 :as percentiles]]
   (percentiles-for-graph item-names percentiles default-percentile-names))
  ([item-names percentiles percentile-names]
   (zipmap percentile-names (map (fn [p]
                                   {:style {:render-style :line :marker-type :none}
                                    :x item-names
                                    :y (repeat (count item-names) p)})
                                 percentiles))))


(defn view-cycle-time
  [work-item-intervals & {:keys [category-title
                                 outliers
                                 from-state
                                 to-state]
                          :or   {category-title "Dev Cycle Time"
                                 outliers       #{}
                                 from-state     "Active"
                                 to-state       "Closed"}
                          :as options}]
  (let [valid-intervals (select-keys work-item-intervals
                                     (remove outliers (keys work-item-intervals)))
        cycle-time (core/cycle-times valid-intervals from-state to-state)
        item-names (map name (keys cycle-time))
        percentiles (istats/quantile (remove nil? (vals cycle-time)) :probs default-percentiles)
        percentiles-graph-spec (percentiles-for-graph item-names percentiles)
        chart (c/category-chart
               (merge
                {category-title (zipmap (map name (keys cycle-time)) (vals cycle-time))}
                percentiles-graph-spec)

               (merge {:title "Cycle time control chart"
                       :series-order (apply vector category-title (reverse default-percentile-names))
                       :x-axis {:title "Stories"}
                       :y-axis {:title "Cycle time"
                                :decimal-pattern "## days"}
                       :theme :xchart} options))]

    (c/view chart)
    chart))
