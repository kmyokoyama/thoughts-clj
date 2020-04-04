(ns twitter-clj.application.config
  (:require [outpace.config :refer [defconfig]]))

(defconfig twitter-clj.application.config/http-port 3000)
(defconfig twitter-clj.application.config/http-api-version "v1")
(defconfig twitter-clj.application.config/http-api-path-prefix "")

(def system-config {:http {:port twitter-clj.application.config/http-port
                           :api {:version twitter-clj.application.config/http-api-version
                                 :path-prefix twitter-clj.application.config/http-api-path-prefix}}})