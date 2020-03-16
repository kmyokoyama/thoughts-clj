(ns twitter-clj.application.app
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :as core]
            [twitter-clj.application.port.storage :as storage]
            [twitter-clj.application.util :refer [success error process]]))

(defrecord App [storage]
  component/Lifecycle
  (start [this]
    (log/info "Starting app")
    this)

  (stop [this]
    (log/info "Stopping app")
    this))

(defn make-app ;; Constructor.
  []
  (map->App {}))

;; We can make it part of a protocol.

(defn add-user
  [app name email nickname]
  (->
    (core/new-user name email nickname)
    (core/update-user! (:storage app))))

(defn get-user-by-id
  [app user-id]
  (storage/fetch-user-by-id! (:storage app) user-id))

(defn add-tweet
  [app user-id text]
  (let [{:keys [tweet thread] :as tweet-thread} (core/new-tweet user-id text)]
    (core/update-tweet! tweet (:storage app))
    (core/update-thread! thread (:storage app))
    tweet-thread))

(defn get-tweet-by-id
  [app tweet-id]
  (storage/fetch-tweet-by-id! (:storage app) tweet-id))

(defn get-tweets-by-user
  [app user-id]
  (storage/fetch-tweets-by-user! (:storage app) user-id))

(defn like
  [app tweet-id]
  (if-let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)]
    (success (core/like tweet))
    (error nil)))

;(like [this tweet-id])
;(unlike [this tweet-id])
;(retweet [this user-id tweet-id])
;(reply [this reply-text source-tweet-id])