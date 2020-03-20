(ns twitter-clj.application.core
  (:require [twitter-clj.application.port.storage :as storage])
  (:import (java.util UUID)
           (java.time ZonedDateTime)))

(declare new-thread)

(defrecord User [id active name email username])
(defrecord Tweet [id user-id text publish-date likes retweets replies])
(defrecord RetweetWithComment [tweet original-tweet-id])
(defrecord Retweet [id user-id original-tweet-id publish-date])
(defrecord TweetLike [id created-at user-id tweet-id])

;; Tweet-related functions.

(defn new-tweet
  [user-id text]
  (let [tweet-id (UUID/randomUUID)]
    (->Tweet tweet-id user-id text (ZonedDateTime/now) 0 0 0)))

(defn new-like
  [user-id tweet-id]
  (->TweetLike (UUID/randomUUID) (ZonedDateTime/now) user-id tweet-id))

(defn like
  [tweet]
  (update tweet :likes inc))

(defn unlike
  [tweet]
  (if (pos? (:likes tweet))
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

;; Reply-related functions.

(defn reply
  [tweet]
  (update tweet :replies inc))

;; User-related functions.

(defn new-user
  [name email username]
  (->User (UUID/randomUUID) true name email username))