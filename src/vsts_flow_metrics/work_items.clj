(ns vsts-flow-metrics.work-items
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.format :as f]))


(defn cycle-time
  "Computes cycle time from from-state to to-state, defined as
   From the first time, the item transitioned into from-state
   until the last time the item transitioned into to-state.
  "
  [state-intervals from-state to-state]
  (if-let [begun (get state-intervals from-state)]
    (if-let [closed (get state-intervals to-state)]
      (let [first-begin (t/start (first begun))
            last-closed (t/start (last closed))
            interval (t/interval first-begin last-closed)]
        {:interval interval
         :days (t/in-days interval)
         :hours (t/in-hours interval)}))))

(defn intervals-in-state
  "Maps a set of change records for a work item (i.e. VSTS updates)
   to a map which maps states to a vector of intervals (the times where the work item
  was in that state).
  Inserts an implicit interval from when the item's last transition to now.
  "
  [change-records]
  (let [compute-intervals
        (fn [acc change]
          (let [fields (:fields change)
                {next-change-date :newValue} (:System.ChangedDate fields)
                ;; "9999-01-01T00:00:00Z" means "now"
                timestamp (if (re-matches #"^9999-.+" next-change-date)
                            (t/now)
                            (try ;:date-time or :date-time-no-ms
                              (f/parse (f/formatters :date-time) next-change-date)
                              (catch java.lang.IllegalArgumentException e
                                (f/parse (f/formatters :date-time-no-ms) next-change-date))))
                last-board-state (:last-board-state acc)
                last-timestamp (:last-timestamp acc)
                {new-board-state :newValue}   (:System.State fields)
                {new-board-column :newValue}  (:System.BoardColumn fields)
                new-state (or new-board-column new-board-state)
                next-acc  {:last-board-state new-state :last-timestamp timestamp}]
            (if last-timestamp ;; i.e. this is not the first change made to the item
              (if new-state
                (let [duration (t/interval last-timestamp timestamp)
                      board-state-duration (get acc last-board-state [])]
                  (merge acc next-acc
                         {last-board-state (conj board-state-duration duration)}))
                acc)
              next-acc)))]

    (let [time-spent-by-state (reduce compute-intervals {} change-records)]
      ;; create an extra synthetic event to represent now
      ;; this ensures we calculate time spent in last known state
      ;; until (t/now)
      (compute-intervals time-spent-by-state
         (update-in (last change-records)
                    [:fields :System.ChangedDate :newValue]
                    (constantly "9999-01-01T00:00:00Z"))))))


(defn days-spent-in [interval-seq]
  (/ (->> interval-seq
          (map t/in-hours)
          (reduce + 0))
     24.0))

(defn days-spent-in-states
  [time-spent-data]
  (let [states (filter string? (keys time-spent-data))]
    (zipmap states
            (map #(days-spent-in (get time-spent-data % [])) states))))
