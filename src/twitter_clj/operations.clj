(ns twitter-clj.operations
  (:require [twitter-clj.core :as core]
            [twitter-clj.storage.in-mem :as storage.in-mem]
            [twitter-clj.storage :as storage]))

(def storage (storage.in-mem/->InMemoryStorage))

(defn create-user
  [name email nickname]
  (->
    (core/new-user name email nickname)
    (core/update-user! storage)))

(defn get-users
  []
  (storage/fetch-users! storage))

(defn add-tweet
  [user-id text]
  (->
    (core/new-tweet user-id text)
    (core/update-tweet! storage)))

(defn get-tweets-by-user
  [user-id]
  (storage/fetch-tweets-by-user! storage user-id))

(defn value-writer
  [key value]
  (if (some #(= key %) [:id :user-id :thread-id])
    (str value)
    (if (some #(= key %) [:publish-date])
      (str value)
      value)))


;(like [this tweet-id])
;(unlike [this tweet-id])
;(retweet [this user-id tweet-id])
;(reply [this reply-text source-tweet-id])