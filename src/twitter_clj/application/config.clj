(ns twitter-clj.application.config
  (:require [environ.core :refer [env]]
            [taoensso.timbre :as log]))

(def http-host (env :http-host))
(def http-port (Integer/parseInt (env :http-port)))
(def http-api-version (env :http-api-version))
(def http-api-path-prefix (env :http-api-path-prefix))
(def http-api-jws-secret (env :http-api-jws-secret))
(def datomic-uri (env :datomic-uri))
(def redis-uri (env :redis-uri))
(def test-type (env :test-type))

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