(ns vsts-flow-metrics.core
  (:require [vsts-flow-metrics.work-items :as work-items]
            [vsts-flow-metrics.pull-requests :as pull-requests]
            [vsts-flow-metrics.storage :as storage]
            [vsts-flow-metrics.api :as api]
            [vsts-flow-metrics.config :as cfg]
            [vsts-flow-metrics.charts :as charts]
            [vsts-flow-metrics.utils :as utils]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(defn map-values
  [f m]
  (reduce-kv (fn [m k v] (assoc m k (f v))) {} m))


(defn intervals-in-state
  [work-items-changes]
  (cond (empty? work-items-changes)
        {}

        (sequential? (first (vals work-items-changes))) ;; flat query
        (map-values work-items/intervals-in-state work-items-changes)

        (map? (first (vals work-items-changes))) ;; relational query
        (into {} (map (fn [[id parent]]
                        (let [parent-changes (work-items/intervals-in-state (:changes parent))
                              child-changes (intervals-in-state (:children parent))]
                          [id
                           {:changes parent-changes
                            :children child-changes}]))
                      work-items-changes))
        :else
        (throw (RuntimeException. (str "Unexpected format for work-item-changes: " (first (vals work-items-changes)))))))

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

(defn aggregate-flow-efficiency
  ([rel-state-intervals]
   (aggregate-flow-efficiency rel-state-intervals (:aggregate-flow-efficiency (cfg/config))))
  ([rel-state-intervals {:keys [active-states blocked-states field] :as options}]
   (map-values
    (fn [parent]
      (let [child-eff (flow-efficiency (:children parent) options)
            aggregate (apply merge-with + (vals child-eff))]
        (assoc aggregate
               :flow-efficiency
               (if-not (zero? (+ (:active aggregate) (:blocked aggregate)))
                 (double (/ (:active aggregate)
                            (+ (:active aggregate) (:blocked aggregate))))
                        1.0))))
    rel-state-intervals)))

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

(defn pull-requests-cycle-time
  [pull-requests]
  (zipmap (map :pullRequestId pull-requests)
          (map pull-requests/cycle-time pull-requests)))

(defn augment-pull-request-with-threads
  [pull-request]
  (let [threads (api/get-pull-request-threads
                 (cfg/vsts-instance)
                 (cfg/vsts-project)
                 (api/get-repository-by-name (cfg/vsts-instance)
                                             (cfg/vsts-project)
                                             (get-in (cfg/config) [:pull-requests :repository]))
                 pull-request)]
    (assoc pull-request :threads threads)))

(def ^:static pr-responsiveness-time-units
  {:days t/in-days :hours t/in-hours :mins t/in-minutes})

(defn unit-function-for-first-vote-responsiveness
  [unit]
  (let [f (get pr-responsiveness-time-units (keyword unit))]
    (when-not f
      (throw (RuntimeException. ":pull-request-responsiveness :responsiveness-time-unit must be days hours or mins.")))
    f))

(defn pull-requests-first-vote-responsiveness
  [pull-requests team]
  (let [unit (get-in (cfg/config) [:pull-request-responsiveness :responsiveness-time-unit])
        unit-fn (unit-function-for-first-vote-responsiveness unit)
        augmented-prs (map augment-pull-request-with-threads
                           pull-requests)
        team-responsiveness (zipmap (map :pullRequestId pull-requests)
                                    (map #(pull-requests/first-vote-responsiveness % team)
                                         augmented-prs))]
    (map-values #(let [responded (filter :responsiveness (vals %))
                       responsiveness-mins (map (comp unit-fn :responsiveness)
                                                responded)]
                   (if (seq responsiveness-mins)
                     (apply min responsiveness-mins)
                     -1.0))
                team-responsiveness)))


;; experimental
(defn pull-requests-work-items
  [pull-requests repo]
  (map
   #(let [work-items (api/get-pull-request-work-items
                      (cfg/vsts-instance)
                      (cfg/vsts-project)
                      repo
                      %)
          work-items-normalized (if (seq work-items)
                                  (map-values work-items/intervals-in-state work-items)
                                  (list))]
      work-items-normalized)
   pull-requests))

(defn pull-requests-threads
  [pull-requests repo]
  (map
   #(api/get-pull-request-threads
     (cfg/vsts-instance)
     (cfg/vsts-project)
     repo
     %)
   pull-requests))

(defn augment-pull-request-with-linked-work-items
  [pull-req repo]
  (assoc pull-req
         :linked-work-items
         (first (pull-requests-work-items (list pull-req) repo))))


(defn interesting-times
  [cfg]
  (let [ago (:ago cfg)
        step (:step cfg)]
    (map (fn [ago] (t/minus (t/now) (t/days ago))) (range 0 (inc ago) step))))


(defn augment-pull-request [repo pull-req]
  (let [iterations (api/get-pull-request-iterations (cfg/vsts-instance) (cfg/vsts-project)
                                                    repo pull-req)
        iterations-timestamped (map #(assoc %
                                            :type :iteration
                                            :timestamp
                                            (utils/parse-time-stamp (:createdDate %)))
                                    iterations)
        threads (api/get-pull-request-threads
                 (cfg/vsts-instance)
                 (cfg/vsts-project)
                 repo
                 pull-req)

        threads-timestamped (map
                             #(assoc %
                                     :type :thread
                                     :timestamp
                                     (utils/parse-time-stamp (:publishedDate %)))
                             threads)

        events (sort-by :timestamp (concat threads-timestamped iterations-timestamped))

        created-at (utils/parse-time-stamp (:creationDate pull-req))
        completed-at (utils/parse-time-stamp (:closedDate pull-req))]

    (assoc pull-req :events events
           :creationDate created-at
           :closedDate completed-at)))


(defn augment-pull-requests
  [pull-requests]
  (let [repo (api/get-repository-by-name
              (cfg/vsts-instance)
              (cfg/vsts-project)
              (get-in (cfg/config) [:pull-requests :repository]))]
    (map #(augment-pull-request repo %) pull-requests)))
