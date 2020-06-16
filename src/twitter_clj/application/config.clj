(ns twitter-clj.application.config
  (:require [outpace.config :refer [defconfig]]
            [taoensso.timbre :as log]))

(defconfig http-host)
(defconfig http-port)
(defconfig http-api-version)
(defconfig http-api-path-prefix)
(defconfig http-api-jws-secret)
(defconfig datomic-uri)
(defconfig redis-uri)

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