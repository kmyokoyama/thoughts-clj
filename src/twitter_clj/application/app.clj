(ns twitter-clj.application.app
  (:require [twitter-clj.application.core :as core]
            [twitter-clj.adapter.storage.in-mem :as storage.in-mem]
            [twitter-clj.application.port.storage :as storage]
            [com.stuartsierra.component :as component]))

;(def storage (storage.in-mem/->InMemoryStorage))

(defrecord App [storage]
  component/Lifecycle
  (start [this]
    (println "Starting app.")
    this)

  (stop [this] ;; TODO: Add shutdown.
    (println "Stopping app.")
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

(defn get-users
  [app]
  (vals (storage/fetch-users! (:storage app))))

(defn add-tweet
  [app user-id text]
  (->
    (core/new-tweet user-id text)
    (core/update-tweet! (:storage app))))

(defn get-tweets-by-user
  [app user-id]
  (storage/fetch-tweets-by-user! (:storage app) user-id))

(defn like
  [app tweet-id]
  (let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)
        updated-tweet (core/like tweet)]
    updated-tweet))

;; Not part of the App API.

(defn is-better-str
  [key]
  (or
    (= key :id)
    (some #(.endsWith (str key) %) ["-id", "-date"])))

(defn value-writer
  [key value]
  (if (is-better-str key)
    (str value)
    value))

;(like [this tweet-id])
;(unlike [this tweet-id])
;(retweet [this user-id tweet-id])
;(reply [this reply-text source-tweet-id])