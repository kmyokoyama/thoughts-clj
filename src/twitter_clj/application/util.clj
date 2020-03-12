(ns twitter-clj.application.util)

(defn set-status [status result] (merge {:status status} result))
(defn success [] (partial set-status :ok))
(defn error [] (partial set-status :error))