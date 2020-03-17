(ns twitter-clj.application.core
  (:require [twitter-clj.application.port.storage :as storage])
  (:import (java.util UUID)
           (java.time ZonedDateTime)))

(declare new-thread)

(defrecord User [id active name email username])
(defrecord Tweet [id user-id text publish-date likes retweets replies thread-id])
(defrecord RetweetWithComment [tweet original-tweet-id])
(defrecord Retweet [id user-id original-tweet-id publish-date])
(defrecord TwitterThread [id source-tweet-id tweet-replies])

;; Tweet-related functions.

(defn new-tweet
  [user-id text]
  (let [tweet-id (UUID/randomUUID)
        thread (new-thread tweet-id)
        thread-id (:id thread)]
    {:tweet (->Tweet tweet-id user-id text (ZonedDateTime/now) 0 0 0 thread-id)
     :thread thread}))

(defn like
  [tweet]
  (update tweet :likes inc))

(defn unlike
  [{likes :likes :as tweet}]
  (if (pos? likes)
    (update tweet :likes dec)
    tweet))

;; Retweet-related functions.

(defn new-retweet
  [user-id original-tweet-id]
  (->Retweet (UUID/randomUUID) user-id original-tweet-id (ZonedDateTime/now)))

(defn- new-retweet-with-comment
  [tweet original-tweet-id]
  (->RetweetWithComment tweet original-tweet-id))

(defn retweet-only
  [user-id original-tweet]
  {:retweet (new-retweet user-id (:id original-tweet))
   :retweeted (update original-tweet :retweets inc)})

(defn retweet-with-comment
  [user-id text original-tweet]
  (let [{:keys [tweet thread]} (new-tweet user-id text)
        retweet (new-retweet-with-comment tweet (:id original-tweet))]
    {:retweet retweet
     :thread thread
     :retweeted (update original-tweet :retweets inc)}))

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

(defn reply
  [reply-tweet source-tweet thread]
  (let [thread' (-> thread (add-reply-tweet-to-thread (:id reply-tweet)))
        source-tweet' (-> source-tweet (update :replies inc))]
    {:reply-tweet reply-tweet
     :source-tweet source-tweet'
     :thread thread'}))

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
  [name email username]
  (->User (UUID/randomUUID) true name email username))

(defn update-user!
  [user storage]
  (storage/update-user! storage user)
  user)