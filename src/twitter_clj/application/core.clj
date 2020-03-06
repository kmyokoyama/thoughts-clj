(ns twitter-clj.application.core
  (:require [twitter-clj.application.port.storage :as storage])
  (:import (java.util UUID)
           (java.time ZonedDateTime)))

(defrecord User [id active name email nickname])
(defrecord Tweet [id user-id text publish-date likes retweets replies thread-id])
(defrecord RetweetWithComment [tweet original-tweet-id])
(defrecord Retweet [id user-id original-tweet-id publish-date])
(defrecord TwitterThread [id source-tweet-id tweet-replies])

;; Tweet-related functions.

(defn new-tweet
  [user-id text]
  (->Tweet (UUID/randomUUID) user-id text (ZonedDateTime/now) 0 0 0 nil))

(defn new-retweet
  [user-id original-tweet-id]
  (->Retweet (UUID/randomUUID) user-id original-tweet-id (ZonedDateTime/now)))

(defn- new-retweet-with-comment
  [user-id text original-tweet-id]
  (->RetweetWithComment (new-tweet user-id text) original-tweet-id))

(defn like
  [tweet]
  (update tweet :likes inc))

(defn unlike
  [{likes :likes :as tweet}]
  (if (pos? likes)
    (update tweet :likes dec)
    tweet))

(defn retweet
  [user-id original-tweet]
  [(new-retweet user-id (:id original-tweet))
   (update original-tweet :retweets inc)])

(defn retweet-with-comment
  [user-id text original-tweet]
  [(new-retweet-with-comment user-id text (:id original-tweet))
   (update original-tweet :retweets inc)])

(defn update-tweet!
  [tweet storage]
  (storage/update-tweet! storage tweet)
  tweet)

;; Thread-related functions.

(defn new-thread
  [source-tweet-id]
  (->TwitterThread (UUID/randomUUID) source-tweet-id []))

(defn- add-reply-tweet-to-thread
  [thread tweet-id]
  (update thread :tweet-replies conj tweet-id))

(defn- add-thread-to-source-tweet
  [tweet thread-id]
  (assoc tweet :thread-id thread-id))

(defn reply
  [reply-tweet source-tweet thread]
  (let [thread' (-> thread (add-reply-tweet-to-thread (:id reply-tweet)))
        source-tweet' (-> source-tweet
                          (add-thread-to-source-tweet (:id thread'))
                          (update :replies inc))]
    [reply-tweet source-tweet' thread']))

(defn fetch-thread!
  [storage source-tweet]
  (let [thread-id (:thread-id source-tweet)]
    (if (nil? thread-id)
      (new-thread (:id source-tweet))
      (storage/fetch-thread-by-id! storage thread-id))))

(defn update-thread!
  [thread storage]
  (storage/update-thread! storage thread)
  thread)

;; User-related functions.

(defn new-user
  [name email nickname]
  (->User (UUID/randomUUID) true name email nickname))

(defn update-user!
  [user storage]
  (storage/update-user! storage user)
  user)