(ns vsts-flow-metrics.pull-requests
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [vsts-flow-metrics.utils :as utils]))

(def file-format (f/formatters :date-hour-minute))
(def as-of-format (f/formatter "MM/dd/yyyy"))

(defn normalize-pull-request
  [pull-request]
  (-> pull-request
      (update :creationDate utils/parse-time-stamp)
      (update :closedDate
              #(if % (utils/parse-time-stamp %)))))

(defn cycle-time
  "Computes cycle time from from-state to to-state, defined as
   From the first time, the item transitioned into from-state
   until the last time the item transitioned into to-state.
  "
  [pull-request]
  (if-let [created (:creationDate pull-request)]
    (if-let [closed (:closedDate pull-request)]
      (let [interval (t/interval created closed)]
        {:interval interval
         :days  (/ (t/in-minutes interval)   60.0 24.0)
         :hours (/ (t/in-minutes interval)   60.0)
         :mins  (t/in-minutes interval)}))))


(defn print-pr-thread [threads]
  (let [i (atom 0)]
    (loop [ts threads]
      (when (seq ts)
        (println "-------- " @i " -----------")

       ; (clojure.pprint/pprint (:CodeReviewThreadType (:properties (first ts))))
        (clojure.pprint/pprint (:properties (first ts)))

        (swap! i inc)
        (recur (rest ts))))))

(def ^:static reviewers-update "ReviewersUpdate")
(def ^:static status-update "StatusUpdate")
(def ^:static vote-cast "VoteUpdate")

(defn reviewer-assigned?
  [upd]
  (let [thread-type (get-in upd [:properties :CodeReviewThreadType :$value])]
    (and (= reviewers-update thread-type)
         (get-in upd [:properties :CodeReviewReviewersUpdatedAddedTfId :$value]))))

(defn completed?
  [upd]
  (let [thread-type (get-in upd [:properties :CodeReviewThreadType :$value])]
    (and (= status-update thread-type)
         (= "Completed" (get-in upd [:properties :CodeReviewStatus :$value])))))

(defn vote-cast?
  [upd]
  (let [thread-type (get-in upd [:properties :CodeReviewThreadType :$value])]
    (= vote-cast thread-type)))

(defn voter?
  [reviewer vote]
  (let [voter-id (get-in vote [:properties
                               :CodeReviewVotedByTfId
                               :$value])
        voter-ref (get-in vote [:properties
                                :CodeReviewVotedByIdentity
                                :$value])]
    (or (= voter-id (:id reviewer))
        (and voter-ref
             (= (:id reviewer)
                (:id
                 (get (:identities vote) (keyword voter-ref))))))))

(defn reviewers-assigned
  [threads reviewers initial-time]
  (let [is-update? (fn [t]
                     (let [thread-type (get-in t [:properties :CodeReviewThreadType :$value])]
                       (= reviewers-update thread-type)))
        updates (filter is-update? threads)

        reduce-updates (fn [acc upd]
                         (let [reviewer-id (get-in upd [:properties
                                                        :CodeReviewReviewersUpdatedAddedTfId
                                                        :$value])
                               display-name (get-in upd [:properties
                                                         :CodeReviewReviewersUpdatedAddedDisplayName
                                                         :$value])
                               event-time (utils/parse-time-stamp (:publishedDate upd))]
                           (assoc acc reviewer-id {:id reviewer-id
                                                   :displayName display-name
                                                   :added-at event-time})))
        assigned-reviewers (reduce reduce-updates {} updates)
        final-reviewer-ids (into #{} (map :id reviewers))
        initial-reviewer-ids (clojure.set/difference final-reviewer-ids (into #{} (keys assigned-reviewers)))
        initial-reviewers (filter #(contains? initial-reviewer-ids (:id %)) reviewers)]
    (merge assigned-reviewers
           (zipmap (seq initial-reviewer-ids)
                   (map #(assoc % :added-at initial-time) initial-reviewers)))))

;https://docs.microsoft.com/en-us/rest/api/vsts/git/pull%20requests/get%20pull%20request#IdentityRefWithVote
;Vote on a pull request:
;10 - approved 5 - approved with suggestions 0 - no vote -5 - waiting for author -10 - rejected

(def pull-request-votes
  { 10  :approved
    5   :approved-with-suggestions
    0   :no-vote
   -5   :waiting-for-author
   -10  :rejected})


(defn transition-pr-state
  [last-state upd current-votes final-reviewers]
  (case last-state
    "New"
    (cond
      (reviewer-assigned? upd)
      "In Review"

      (completed? upd)
      "Completed"

      :else
      "New")

    "In Review"
    (cond
      (vote-cast? upd)
      "Partially Reviewed"

      (completed? upd)
      "Completed"

      :else
      "In Review")

    "Fully Reviewed"

    "Completed"

    last-state))

(defn intervals-in-state [pull-req]
  (let [created-at (f/parse (f/formatter :date-time) (:creationDate pull-req))
        completed-at (f/parse (f/formatter :date-time) (:closedDate pull-req))
        final-reviewers (:reviewers pull-req)
        final-reviewer-ids (into #{} (map :id final-reviewers))
        reviewers-assigned-at (reviewers-assigned (:threads pull-req) final-reviewers created-at)
        reviewer-assigned-sequence (sort-by :added-at (vals reviewers-assigned-at))
        initial-reviews (zipmap (map :id final-reviewers) (repeat :unassigned))
        initial-state (if (= created-at (:added-at (first reviewer-assigned-sequence)))
                        "In Review"
                        "New")
        initial-state {:last-timestamp created-at
                       :last-state initial-state
                       "New" []
                       "In Review" []
                       "Partially Reviewed" []
                       "Fully Reviewed" []
                       "Completed" []}
        reduce-update
        (fn [acc upd]
          (let [{:keys [last-timestamp last-state current-votes]} acc
                next-state (transition-pr-state last-state upd current-votes final-reviewer-ids)
                timestamp (f/parse (f/formatter :date-time) (:publishedDate upd))
                interval (t/interval last-timestamp timestamp)]
            (if (= last-state next-state)
              acc
              (-> acc
                  (assoc :last-timestamp timestamp :last-state next-state)
                  (update last-state #(conj % interval))))))]
    (reduce reduce-update initial-state (:threads pull-req))))


(defn first-vote-responsiveness
  [pull-req team]
  (let [created-at (:creationDate pull-req)
        votes (filter vote-cast? (:threads pull-req))
        final-reviewers (:reviewers pull-req)
        team-reviewers (->> final-reviewers
                            (filter (fn [reviewer]
                                      (if-let [voted-for (seq (:votedFor reviewer))]
                                        (seq (filter #(= (:id team) (:id %)) voted-for))))))

        team-reviewer-ids (into #{} (map :id team-reviewers))
        reviewers-assigned-at (reviewers-assigned (:threads pull-req) final-reviewers created-at)
        team-assigned-at (get-in reviewers-assigned-at [(:id team) :added-at])
        responsiveness (fn [r assigned-at votes]
                         (let [r-votes (filter #(voter? r %) votes)]
                           (->> r-votes
                                (map #(let [voted-at (utils/parse-time-stamp (:publishedDate %))]
                                        (assoc %
                                               :voted-at
                                               voted-at
                                               :responsiveness
                                               (if (and voted-at
                                                        (t/after? voted-at assigned-at))
                                                 (t/interval assigned-at voted-at)))))
                                (sort-by :voted-at)
                                first)))


        ]

    (cond (nil? team-assigned-at)
          nil
          (empty? team-reviewers)
          nil

          :else
          (->> team-reviewers
               (map (fn [reviewer]
                      [(:id reviewer)
                       (responsiveness reviewer
                                       team-assigned-at
                                       votes)]))
               (into {})))))
