(ns twitter-clj.application.config
  (:require [outpace.config :refer [defconfig]]))

(defconfig twitter-clj.application.config/http-port 3000)

(def system-config {:http {:port twitter-clj.application.config/http-port}})