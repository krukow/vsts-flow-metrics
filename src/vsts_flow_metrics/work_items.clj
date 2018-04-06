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

(defn- update-state
  [intervals-record new-state new-timestamp]
  (let [last-state (:last-state intervals-record)
        last-timestamp (:last-timestamp intervals-record)
        updated-record (-> intervals-record
                           (assoc :last-state new-state)
                           (assoc :last-timestamp new-timestamp))]
    (when (nil? last-timestamp)
      (throw (RuntimeException. (str "Last timestamp is nil " intervals-record))))
    (if new-state
      (if last-state
        (assoc updated-record last-state (conj (get intervals-record last-state [])
                                               (t/interval last-timestamp new-timestamp)))
        updated-record)
      intervals-record)))

(defn- update-board-state
  [intervals-record new-state new-done-state new-timestamp]
  (let [last-state (:last-state intervals-record)
        last-timestamp (:last-timestamp intervals-record)
        updated-state (update-state intervals-record new-state new-timestamp)]

    (if (and (not (nil? new-done-state)) ;; done/not done bit set
             (or (nil? new-state) ;; there must be no state change (nil new state)
                 (= last-state new-state))) ;; there must be no state change (old and new state same)
      ;; column flip (state remains and done bit is flipped)
      (cond (true? new-done-state)
            ;; switched out of Doing into Done
            (let [last-done-state-name (str last-state " - Doing")]
              (assoc updated-state last-done-state-name
                     (conj (get updated-state last-done-state-name [])
                           (t/interval last-timestamp new-timestamp))))

            (false? new-done-state)
            (let [last-done-state-name (str last-state " - Done")]
              ;; switched out of Done into Doing
              (assoc updated-state last-done-state-name
                     (conj (get updated-state last-done-state-name [])
                           (t/interval last-timestamp new-timestamp))))

            :else
            (throw (RuntimeException.
                    (str "Unexpected value for :System.BoardColumnDone: " new-done-state))))


      ;; This is not a column flip, just return regular state change
      updated-state)))

(defn intervals-in-state
  "Maps a set of change records for a work item (i.e. VSTS updates)
   to a map which maps states to a vector of intervals (the times where the work item
  was in that state).
  Inserts an implicit interval from when the item's last transition to now.
  "
  [change-records]
  (let [parse-time-stamp (fn [date-s]
                           (if (re-matches #"^9999-.+" date-s)
                            (t/now)
                            (try ;:date-time or :date-time-no-ms
                              (f/parse (f/formatters :date-time) date-s)
                              (catch java.lang.IllegalArgumentException e
                                (f/parse (f/formatters :date-time-no-ms) date-s)))))

        compute-intervals
        (fn [acc change]
          (let [fields (:fields change)
                {next-change-date :newValue} (:System.ChangedDate fields)
                ;; "9999-01-01T00:00:00Z" means "now"
                timestamp (parse-time-stamp next-change-date)

                system-state (get acc :System.State           {})
                board-state  (get acc :System.BoardColumn     {})


                {new-state :newValue}        (:System.State fields)
                {new-board-state :newValue}  (:System.BoardColumn fields)
                {new-done-state :newValue}   (:System.BoardColumnDone fields)
                ;{new-board-lane :newValue}   (:System.BoardLane fields)

                next-system-state (update-state system-state new-state timestamp)
                next-board-state  (update-board-state board-state new-board-state new-done-state timestamp)]
            (merge acc
                   {:System.State next-system-state}
                   {:System.BoardColumn next-board-state})))]

    (let [first-change (first change-records)
          changes (rest change-records)
          first-time-stamp (parse-time-stamp
                            (get-in first-change [:fields :System.ChangedDate :newValue]))
          initial-state {:System.State {:last-timestamp first-time-stamp
                                        :last-state
                                        (get-in first-change
                                                [:fields :System.State :newValue])}

                         :System.BoardColumn {:last-timestamp first-time-stamp
                                              :last-state
                                              (get-in first-change
                                                      [:fields :System.BoardColumn :newValue])}}
          time-spent-by-state (reduce compute-intervals initial-state changes)

          ;; create an extra synthetic event to represent now
          ;; this ensures we calculate time spent in last known state
          ;; until (t/now)
          synthetic-auto-transition {:fields
                                     {:System.ChangedDate {:newValue "9999-01-01T00:00:00Z"}
                                      :System.State       {:newValue
                                                           (get-in
                                                            time-spent-by-state
                                                            [:System.State :last-state])}
                                      :System.BoardColumn {:newValue
                                                           (get-in
                                                            time-spent-by-state
                                                            [:System.BoardColumn :last-state])}
                                      :System.BoardLane   {:newValue
                                                           (get-in
                                                            time-spent-by-state
                                                            [:System.BoardLane :last-state])}
                                      :System.BoardColumnDone
                                      {:newValue
                                       (get-in
                                        time-spent-by-state
                                        [:System.BoardColumnDone :last-state])}}}]

      (compute-intervals time-spent-by-state synthetic-auto-transition))))


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

(defn flow-efficiency [time-spent-data active-states blocked-states]
  (let [time-spent-in-days (days-spent-in-states time-spent-data)
        days-spent-active   (reduce + 0 (map #(get time-spent-in-days % 0.0) active-states))
        days-spent-blocked  (reduce + 0 (map #(get time-spent-in-days % 0.0) blocked-states))]
    {:active days-spent-active
     :blocked days-spent-blocked
     :flow-efficiency (if-not (zero? (+ days-spent-blocked days-spent-active))
                        (double (/ days-spent-active
                                   (+ days-spent-blocked days-spent-active)))
                        1.0)}))

(defn find-transitions [from to]
  (for [f from t to
        :when (= (t/end f)
                 (t/start t))]
    f))

(defn responsiveness [time-spent-data from-state to-state]
  "Computes how fast the item transitioned from `from-state` to `to-state`.
Returns nil if the item did not transition.
If the item transitioned multiple times, we return the first transition's duration."
  (if-let [from (seq (get time-spent-data from-state))]
    (if-let [to (seq (get time-spent-data to-state))]
      (if-let [transition (first (find-transitions from to))]
        (let [begin (t/start transition)
              end (t/end transition)
              duration transition]
          {:interval duration
           :in-days (/ (t/in-hours duration) 24.0)
           :in-hours (t/in-hours duration)})))))

(defn work-items-states
  [work-items state]
  (map #(get-in % [:fields state]) work-items))
