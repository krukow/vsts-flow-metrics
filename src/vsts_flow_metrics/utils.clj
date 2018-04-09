(ns vsts-flow-metrics.utils
  (:require [clj-time.core :as t]
            [clj-time.format :as f]))

(defn parse-time-stamp
  [date-s]
  (if (re-matches #"^9999-.+" date-s)
    (t/now)
    (try ;:date-time or :date-time-no-ms
      (f/parse (f/formatters :date-time) date-s)
      (catch java.lang.IllegalArgumentException e
        (f/parse (f/formatters :date-time-no-ms) date-s)))))
