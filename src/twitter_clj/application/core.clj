(ns twitter-clj.application.core
  (:import (java.util UUID)
           (java.time ZonedDateTime)))

(declare new-thread)

(defrecord User [id active name email username])
(defrecord Tweet [id user-id text publish-date likes retweets replies])
(defrecord Retweet [id user-id has-comment comment publish-date tweet])
(defrecord TweetLike [id created-at user-id tweet-id])

;; Tweet-related functions.

(defn new-tweet
  [user-id text]
  (let [tweet-id (str (UUID/randomUUID))]
    (->Tweet tweet-id user-id text (ZonedDateTime/now) 0 0 0)))

(defn new-like
  [user-id tweet-id]
  (->TweetLike (str (UUID/randomUUID)) (ZonedDateTime/now) user-id tweet-id))

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
  ([user-id retweeted]
   (->Retweet (str (UUID/randomUUID)) user-id false nil (ZonedDateTime/now) retweeted))

  ([user-id retweeted comment]
   (->Retweet (str (UUID/randomUUID)) user-id true comment (ZonedDateTime/now) retweeted)))

(defn retweet
  [retweeted]
  (update retweeted :retweets inc))

;; Reply-related functions.

(defn reply
  [tweet]
  (update tweet :replies inc))

;; User-related functions.

(defn new-user
  [name email username]
  (->User (str (UUID/randomUUID)) true name email username))