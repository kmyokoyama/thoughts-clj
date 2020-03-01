(ns twitter-clj.application.operations
  (:require [twitter-clj.application.core :as core]
            [twitter-clj.adapter.storage.in-mem :as storage.in-mem]
            [twitter-clj.application.port.storage :as storage]))

(def storage (storage.in-mem/->InMemoryStorage))

(defn add-user
  [name email nickname]
  (->
    (core/new-user name email nickname)
    (core/update-user! storage)))

(defn get-users
  []
  (vals (storage/fetch-users! storage)))

(defn add-tweet
  [user-id text]
  (->
    (core/new-tweet user-id text)
    (core/update-tweet! storage)))

(defn get-tweets-by-user
  [user-id]
  (storage/fetch-tweets-by-user! storage user-id))

(defn like
  [tweet-id]
  (let [tweet (storage/fetch-tweet-by-id! storage tweet-id)
        updated-tweet (core/like tweet)]
    (println tweet)
    updated-tweet))

(defn shutdown
  []
  (storage/shutdown! storage))

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