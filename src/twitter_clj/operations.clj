(ns twitter-clj.operations
  (:require [twitter-clj.core :as core]
            [twitter-clj.storage.in-mem :as storage.in-mem]
            [twitter-clj.storage :as storage]))

(def storage (storage.in-mem/->InMemoryStorage))

(defn create-user
  [name email nickname]
  (let [user (core/new-user name email nickname)]
    (core/update-user! user storage)))

(defn get-users
  []
  (storage/fetch-users! storage))

;(add-tweet [this user-id text])
;(like [this tweet-id])
;(unlike [this tweet-id])
;(retweet [this user-id tweet-id])
;(reply [this reply-text source-tweet-id])