(ns vsts-flow-metrics.charts
  (:require [com.hypirion.clj-xchart :as c]
            [incanter.stats :as istats]
            [vsts-flow-metrics.config :as cfg]
            [clojure.java.io :as io]))


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

(defn default-chart-options
  [chart-type]
  (get-in (cfg/config) [chart-type :chart]))

(defn view-cycle-time
  ([cycle-time]
   (view-cycle-time cycle-time (default-chart-options :cycle-time)))
  ([cycle-time options]
   (view-cycle-time cycle-time (default-chart-options :cycle-time) nil)) ;; show graph
  ([cycle-time options opt-filename-or-nil]
   (let [category-title (get options :category-title "Cycle Time")
         item-names (map name (keys cycle-time))
         min-width (+ (* 50 (count item-names)) 500)
         width (or (:width options) min-width)
         width (max width min-width)
         percentiles (istats/quantile (remove nil? (vals cycle-time))
                                      :probs default-percentiles)
         percentiles-graph-spec (percentiles-for-graph item-names percentiles)
         chart (c/category-chart
                (merge {category-title (zipmap item-names (vals cycle-time))} percentiles-graph-spec)
                (merge {:series-order (apply vector category-title
                                             (reverse default-percentile-names))
                        :width width}
                       (dissoc options :width)))]
     (if opt-filename-or-nil
       (c/spit chart (.getAbsolutePath opt-filename-or-nil))
       (c/view chart))
     chart)))


(defn view-time-in-state
  ([times-in-states]
   (view-time-in-state times-in-states (default-chart-options :time-in-state)))
  ([times-in-states options]
   (view-time-in-state times-in-states (default-chart-options :time-in-state) nil)) ;; show graph
  ([times-in-states options opt-filename-or-nil]
   (let [all-states         (set (filter string? (mapcat keys (vals times-in-states))))
         interesting-states (clojure.set/difference all-states
                                                    (set (get options :remove-states #{})))
         item-names (map name (keys times-in-states))
         min-width (+ (* 50 (count item-names)) 500)
         width (or (:width options) min-width)
         width (max width min-width)

         chart (c/category-chart
                (into {}
                  (map
                   (fn [state]
                     (let [time-spent
                           (into
                            {}
                            (map (fn [[id time-spent-in-state]]
                                   [(name id) (get time-spent-in-state state 0.0)])
                                 times-in-states))]
                       [state time-spent]))
                   interesting-states))
                (assoc options :width width))]
     (if opt-filename-or-nil
       (c/spit chart (.getAbsolutePath opt-filename-or-nil))
       (c/view chart))
     chart)))


(defn view-flow-efficiency
  ([flow-efficiency]
   (view-flow-efficiency flow-efficiency (default-chart-options :flow-efficiency)))
  ([flow-efficiency options]
   (view-flow-efficiency flow-efficiency (default-chart-options :flow-efficiency) nil)) ;; show graph
  ([flow-efficiency options opt-filename-or-nil]
   (let [item-names (map name (keys flow-efficiency))
         title (get options :category-title "Flow efficiency")
         percentiles (istats/quantile (remove #(or (zero? %) (= 1.0 %))
                                             (map :flow-efficiency (vals flow-efficiency)))
                                     :probs [0.1 0.2 0.30 0.5 0.80 0.9])
         percentile-names ["10th percentile" "20th percentile" "30th percentile" "median" "80th percentile" "90th percentile"]
         percentiles-graph-spec (percentiles-for-graph item-names percentiles percentile-names)
         min-width (+ (* 50 (count item-names)) 500)
         width (or (:width options) min-width)
         width (max width min-width)

         chart (c/category-chart
                (merge
                 {title
                  (zipmap (map name (keys flow-efficiency))
                          (map :flow-efficiency (vals flow-efficiency)))}
                 percentiles-graph-spec)
                (merge {:series-order (cons title (reverse percentile-names))}
                       (assoc options :width width)))]
     (if opt-filename-or-nil
       (c/spit chart (.getAbsolutePath opt-filename-or-nil))
       (c/view chart))
     chart)))
