(ns vsts-flow-metrics.csv
  (:require [vsts-flow-metrics.config :as cfg]
            [clojure.java.io :as io]
            [incanter.stats :as istats]
            [clojure.data.csv :as csv]))

(defn project-rows
  [rows values]
  (into []
        (concat [(map :header rows)]
                (for [f values]
                  (into
                   []
                   (map #((:fn %) f) rows))))))

(defn work-item-link [id]
  ;https://msmobilecenter.visualstudio.com/Mobile-Center/_workitems/edit/37679
  (str "https://" (:name (cfg/vsts-instance))
       "/"
       (cfg/vsts-project)
       "/_workitems/edit/"
       (name id)))

(defn flow-efficiency
  [flow-efficiency]
  (let [percentiles (istats/quantile (remove #(zero? %)
                                             (map :flow-efficiency (vals flow-efficiency)))
                                     :probs [0.1 0.2 0.30 0.5 0.80 0.9])]
    (project-rows [{:header "Work Item"
                    :fn (fn [[id val]] (str "#" (name id)))}
                   {:header "Flow Efficiency"
                    :fn (fn [[id val]] (:flow-efficiency val))}
                   {:header "Link"
                    :fn (fn [[id val]] (work-item-link id))}

                   {:header "10th percentile"
                    :fn (constantly (nth percentiles 0))}
                   {:header "20th percentile"
                    :fn (constantly (nth percentiles 1))}
                   {:header "30th percentile"
                    :fn (constantly (nth percentiles 2))}
                   {:header "median"
                    :fn (constantly (nth percentiles 3))}
                   {:header "80th percentile"
                    :fn (constantly (nth percentiles 4))}
                   {:header "90th percentile"
                    :fn (constantly (nth percentiles 5))}]
                  flow-efficiency)))

(defn cycle-times
  [cycle-times]
  (let [valid-cycle-times
        (remove #(nil? (second %))
                cycle-times)
        percentiles (istats/quantile (remove #(zero? %)
                                             (vals valid-cycle-times))
                                     :probs [0.1 0.2 0.30 0.5 0.80 0.9])]
    (project-rows [{:header "Work Item"
                    :fn (fn [[id val]] (str "#" (name id)))}
                   {:header "Cycle-time"
                    :fn (fn [[id val]] (float val))}
                   {:header "Link"
                    :fn (fn [[id val]] (work-item-link id))}

                   {:header "10th percentile"
                    :fn (constantly (nth percentiles 0))}
                   {:header "20th percentile"
                    :fn (constantly (nth percentiles 1))}
                   {:header "30th percentile"
                    :fn (constantly (nth percentiles 2))}
                   {:header "median"
                    :fn (constantly (nth percentiles 3))}
                   {:header "80th percentile"
                    :fn (constantly (nth percentiles 4))}
                   {:header "90th percentile"
                    :fn (constantly (nth percentiles 5))}
                   ]
                  valid-cycle-times)))

(defn write-fn-to-file
  [f values filename]
  (with-open [writer (io/writer filename)]
    (let [vs (f values)]
      (csv/write-csv writer vs))))
