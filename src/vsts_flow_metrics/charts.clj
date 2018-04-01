(ns vsts-flow-metrics.charts
  (:require [com.hypirion.clj-xchart :as c]
            [incanter.stats :as istats]
            [vsts-flow-metrics.config :as cfg]
            [clj-time.coerce :as coerce]
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
         percentiles (istats/quantile (remove #(zero? %)
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
       (c/view chart)))))

(defn view-responsiveness
  ([responsiveness]
   (view-responsiveness responsiveness (default-chart-options :responsiveness)))
  ([responsiveness options]
   (view-responsiveness responsiveness (default-chart-options :responsiveness) nil)) ;; show graph
  ([responsiveness options opt-filename-or-nil]
   (let [title (get options :category-title)
         item-names (map name (keys responsiveness))
         min-width (+ (* 50 (count item-names)) 500)
         width (or (:width options) min-width)
         width (max width min-width)
         percentiles (istats/quantile
                      (map :in-days (remove nil? (vals responsiveness)))
                      :probs [0.25 0.5 0.80 0.90])
         percentiles-graph-spec (percentiles-for-graph item-names percentiles)
         chart (c/category-chart
                (merge
                 {title
                  (zipmap item-names
                          (map :in-days (vals responsiveness)))}
                 percentiles-graph-spec)
                (merge {:width width
                        :series-order [title "90th percentile" "80th percentile"
                                       "median" "25th percentile"]} options))]

     (if opt-filename-or-nil
       (c/spit chart (.getAbsolutePath opt-filename-or-nil))
       (c/view chart))
     chart)))

(defn view-lead-time-distribution
  ([lead-time-dist]
   (view-lead-time-distribution lead-time-dist (default-chart-options :lead-time-distribution)))
  ([lead-time-dist options]
   (view-lead-time-distribution lead-time-dist (default-chart-options :lead-time-distribution) nil)) ;; show graph
  ([lead-time-dist options opt-filename-or-nil]
   (let [title (get options :category-title)
         item-names (range (apply min (keys lead-time-dist))
                           (inc (apply max (keys lead-time-dist))))
         min-width (+ (* 50 (count item-names)) 500)
         width (or (:width options) min-width)
         width (max width min-width)
         charted-lead-time-dist (zipmap item-names
                                        (map
                                         (fn [x] (get lead-time-dist x 0))
                                         item-names))
         chart (c/category-chart
                {title charted-lead-time-dist}
                (merge {:title "Lead time distribution"
                        :width width} options))]

     (if opt-filename-or-nil
       (c/spit chart (.getAbsolutePath opt-filename-or-nil))
       (c/view chart))
     chart)))

(defn view-historic-queues
  ([state-dist]
   (view-historic-queues state-dist (default-chart-options :historic-queues)))
  ([state-dist options]
   (view-historic-queues state-dist (default-chart-options :historic-queues) nil)) ;; show graph
  ([state-dist options opt-filename-or-nil]
   (let [title (get options :category-title)
         all-states (into #{} (mapcat (fn [[k v]] (keys v)) state-dist))
         interesting-states (or
                             (:series-order options)
                             (clojure.set/difference all-states
                                                     (set (get options :remove-states #{}))))

         date-keys-sorted (sort (keys state-dist))
         item-names (map (fn [x] (str (coerce/to-local-date x))) date-keys-sorted)
         keynames (map (fn [n name] (str (format "%02d" n) "-" name))
                       (range 1 (inc (count item-names)))
                       item-names)
         min-width (+ (* 150 (count keynames)) 500)
         width (or (:width options) min-width)
         width (max width min-width)

         chart-options (merge {:width width} options)
         chart (c/category-chart
                (into {} (map
                          (fn [state]
                            [state (into {} (map
                                             (fn [k name]
                                               [name (get (get state-dist k)
                                                          state)])
                                             date-keys-sorted keynames))])
                          interesting-states))
                chart-options)]

     (if opt-filename-or-nil
       (c/spit chart (.getAbsolutePath opt-filename-or-nil))
       (c/view chart))
     chart)))
