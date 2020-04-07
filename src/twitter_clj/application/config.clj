(ns twitter-clj.application.config
  (:require [outpace.config :refer [defconfig]]
            [taoensso.timbre :as log]))

(defconfig twitter-clj.application.config/http-port)
(defconfig twitter-clj.application.config/http-api-version)
(defconfig twitter-clj.application.config/http-api-path-prefix)

(def system-config {:http {:port twitter-clj.application.config/http-port
                           :api {:version twitter-clj.application.config/http-api-version
                                 :path-prefix twitter-clj.application.config/http-api-path-prefix}}})

(def timbre-config {:timestamp-opts {:pattern "yyyy-MM-dd'T'HH:mm:ss.SSSX"}
                    :output-fn      (fn [{:keys [timestamp_ level hostname_ msg_]}]
                                      (let [level (clojure.string/upper-case (name level))
                                            timestamp (force timestamp_)
                                            hostname (force hostname_)
                                            msg (force msg_)]
                                        (str "[" level "] " timestamp " @" hostname " - " msg)))})

(defn init-system!
  []
  (log/merge-config! timbre-config))