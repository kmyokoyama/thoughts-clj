(ns thoughts.application.config
  (:require [environ.core :as environ]
            [taoensso.timbre :as log]))

(defn- ->keyword
  ([s]
   (-> s (subs 1) keyword))

  ([s default]
   (try
     (->keyword s)
     (catch Exception _ default))))

(defn- ->int
  ([s]
   (Integer/parseInt s))

  ([s default]
   (try
     (->int s)
     (catch NumberFormatException _ default))))

(def http-host (environ/env :http-host))
(def http-port (->int (environ/env :http-port) 3000))
(def http-api-version (environ/env :http-api-version))
(def http-api-path-prefix (environ/env :http-api-path-prefix))
(def http-api-jws-secret (environ/env :http-api-jws-secret))
(def datomic-uri (environ/env :datomic-uri))
(def redis-uri (environ/env :redis-uri))

(def system-test-mode (->keyword (environ/env :system-test-mode) :in-mem))

(def ^:private timbre-config {:timestamp-opts {:pattern "yyyy-MM-dd'T'HH:mm:ss.SSSX"}
                              :output-fn      (fn [{:keys [timestamp_ level hostname_ msg_]}]
                                                (let [level (clojure.string/upper-case (name level))
                                                      timestamp (force timestamp_)
                                                      hostname (force hostname_)
                                                      msg (force msg_)]
                                                  (str "[" level "] " timestamp " @" hostname " - " msg)))})

(defn init-system!
  []
  (log/merge-config! timbre-config))