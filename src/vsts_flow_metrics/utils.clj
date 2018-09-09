(ns vsts-flow-metrics.utils
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.predicates :as t-pr]
            [clj-time.periodic :as p]))

(defn parse-time-stamp
  [date-s]
  (cond (string? date-s)
        (if (re-matches #"^9999-.+" date-s)
          (t/now)
          (try ;:date-time or :date-time-no-ms
            (f/parse (f/formatters :date-time) date-s)
            (catch java.lang.IllegalArgumentException e
              (f/parse (f/formatters :date-time-no-ms) date-s))))

        (instance? org.joda.time.DateTime date-s)
        date-s


        :else
        (throw (RuntimeException. (str "Unexpected type for " date-s)))))

(def danish-time-zone (t/time-zone-for-id "Europe/Copenhagen"))

;http://www.hildeberto.com/2016/11/how-does-easter-feast-change-every-year.html
(defn calculate-easter-date [year]
  (if (< year 1583)
    (throw (IllegalArgumentException.
               "Year must be greater than 1582"))
    (let [a (rem  year 19)
          b (quot year 100)
          c (rem  year 100)
          d (quot b 4)
          e (rem  b 4)
          f (quot (+ b 8) 25)
          g (quot (+ b (- f) 1) 3)
          h (rem  (+ (* 19 a) b (- d) (- g) 15) 30)
          i (quot c 4)
          k (rem  c 4)
          l (rem  (+ 32 (* 2 e) (* 2 i) (- h) (- k)) 7)
          m (quot (+ a (* 11 h) (* 22 l)) 451)
          n (quot (+ h l (- (* 7 m)) 114) 31)
          p (rem  (+ h l (- (* 7 m)) 114) 31)]
      (t/from-time-zone (t/date-time year n (inc p)) danish-time-zone))))



(defn danish-vacation-days [year]
  ;;https://da.wikipedia.org/wiki/Danske_helligdage
  (let [fixed #{(t/interval ;; 1. januar Nytårsdag
                 (t/from-time-zone (t/date-time year 1 1) danish-time-zone)
                 (t/from-time-zone (t/date-time year 1 2) danish-time-zone))
                (t/interval ;; Julen
                 (t/from-time-zone (t/date-time year 12 25) danish-time-zone)
                 (t/from-time-zone (t/date-time year 12 27) danish-time-zone))}

        easter-sun (calculate-easter-date year)
        easter (t/interval
                (t/from-time-zone (t/minus easter-sun (t/days 3)) danish-time-zone) ;Skærtorsdag
                (t/from-time-zone (t/plus  easter-sun (t/days 2)) danish-time-zone) ; end of 2. påskedag
                )

        prayer-day  ;Store bededag: fredagen 3 uger og 5 dage efter påskedag.
        (t/interval
         (t/plus easter-sun (t/weeks 3) (t/days 5))
         (t/plus easter-sun (t/weeks 3) (t/days 6)))

        kristi-hf-day ;Kristi himmelfartsdag: torsdagen 5 uger og 4 dage efter påskedag.
        (t/interval
         (t/plus easter-sun (t/weeks 5) (t/days 4))
         (t/plus easter-sun (t/weeks 5) (t/days 5)))

        pinsen ;; Pinsedag (til minde om Helligåndens komme): søndagen 7 uger efter påskedag.
               ;; Anden pinsedag: dagen efter pinsedag.
        (t/interval
         (t/plus easter-sun (t/weeks 7))
         (t/plus easter-sun (t/weeks 7) (t/days 2)))]

    (conj fixed easter prayer-day kristi-hf-day pinsen)))


(defn danish-vacation-days-overlap
  [interval]
  (let [years (range (t/year (t/start interval))
                     (inc (t/year (t/end interval))))
        vacation-ints (mapcat #(danish-vacation-days %) years)]
    (->> vacation-ints
         (map #(t/overlap interval %))
         (remove nil?))))

(defn weekends [start end]
  (let [normalized-start (t/with-time-at-start-of-day start)
        day-of-week (t/day-of-week normalized-start)
        next-mon (t/plus normalized-start
                         (t/days (- 8 day-of-week)))
        real-end (if (t-pr/weekend? end)
                   (t/plus (t/with-time-at-start-of-day end)
                           (t/days (- 8 day-of-week))) ; next mon if in weekend
                   end)
        weekend-start (t/plus normalized-start
                              (t/days (- 6 day-of-week)))
        starts (p/periodic-seq weekend-start real-end (t/days 7))
        ends   (p/periodic-seq next-mon      real-end (t/days 7))]
    (map (fn [start end] (t/interval start end)) starts ends)))

(defn vacations-and-holiday-overlap
  [interval]
  (let [start-at (t/start interval)
        end-at (t/end interval)

        ;; weekends that overlap with this interval
        relevant-weekends (weekends start-at (t/plus end-at (t/days 1)))
        vacation-overlaps (danish-vacation-days-overlap interval)]
    (remove nil? vacation-overlaps)))


(defn merge-intervals
  [sorted-intervals]
  (let [merger (fn [{current :current :as acc} interval]
                 (if-let [o (t/overlap current interval)]
                   (let [s1 (t/start current)
                         e1 (t/end current)
                         s2 (t/start interval)
                         e2 (t/end interval)
                         extended (t/interval s1 (t/max-date e1 e2))]
                     (assoc acc :current extended))
                   (-> acc
                       (update :res conj current)
                       (assoc :current interval))))
        merged (reduce merger
                       {:current (first sorted-intervals) :res []}
                       sorted-intervals)]
    (conj (:res merged) (:current merged))))

(defn adjust-for-business-time
  [interval]
  (let [s (t/start interval)
        e (t/end interval)
        free-intervals (vacations-and-holiday-overlap interval)
        weekend-overlaps (map #(t/overlap interval %)
                              (weekends s e))
        sorted-free-time (sort-by t/start (concat free-intervals weekend-overlaps))
        maximal-intervals (merge-intervals sorted-free-time)
        business-time-reducer (fn [{s :current :as acc} next-holiday]
                                (let [sh (t/start next-holiday)
                                      eh (t/end   next-holiday)]
                                  (if-not (t/within? next-holiday s)
                                    (-> acc
                                        (update :res conj (t/interval s sh))
                                        (assoc :current eh))
                                    (assoc acc :current eh))))
        business-time-intervals (reduce business-time-reducer
                                        {:current s :res []}
                                        maximal-intervals)]
    (conj (:res business-time-intervals)
          (t/interval (:current business-time-intervals) e)) ))
