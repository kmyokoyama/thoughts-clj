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
  (let [user (storage/fetch-user-by-id! (:storage app) user-id)
        is-found (not (= {} user))]
    (if is-found (success user) (error {}))))

(defn add-tweet
  [app user-id text] ;; TODO: Handle the case in which there is no user with this user-id.
  (let [{:keys [tweet thread] :as tweet-thread} (core/new-tweet user-id text)]
    (core/update-tweet! tweet (:storage app))
    (core/update-thread! thread (:storage app))
    tweet-thread))

(defn get-tweet-by-id
  [app tweet-id]
  (let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)
        is-found (not (= {} tweet))]
    (if is-found (success tweet) (error {}))))

(defn get-tweets-by-user
  [app user-id] ;; TODO: Handle the case in which there is no user with this user-id.
  (storage/fetch-tweets-by-user! (:storage app) user-id))

(defn like
  [app tweet-id]
  (let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)
        is-found (not (= {} tweet))]
    (if is-found (success (core/like tweet)) (error {}))))

;(unlike [this tweet-id])
;(retweet [this user-id tweet-id])
;(reply [this reply-text source-tweet-id])