(ns vsts-flow-metrics.config
  (:require [clojure.string :as string]
            [clj-http.conn-mgr :as conn-mgr]))

(defn- environment-config
  [name]
  (let [env (System/getenv name)]
    (if (string/blank? env)
      nil
      env)))

(defn access-token
  []
  (environment-config "ACCESS_TOKEN"))

(def http-options
  {:basic-auth (str ":" (access-token))
   :socket-timeout 30000  ;; in milliseconds
   :conn-timeout 10000
   :connection-manager (conn-mgr/make-reusable-conn-manager {})
   :accept :json})

(defn vsts-instance
  []
  {:name (environment-config "VSTS_INSTANCE")
   :http-options http-options})
