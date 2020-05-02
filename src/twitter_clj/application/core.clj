(ns twitter-clj.application.core
  (:require [buddy.hashers :as hashers])
  (:import (java.util UUID)
           (java.time ZonedDateTime)))

(defrecord User [id active name email username])
(defrecord Tweet [id user-id text publish-date likes retweets replies])
(defrecord Retweet [id user-id has-comment comment publish-date source-tweet-id])
(defrecord TweetLike [id created-at user-id source-tweet-id])
(defrecord Session [id user-id created-at]) ;; TODO: Remove it from here.

(defn new-session
  [user-id]
  (->Session (str (UUID/randomUUID)) user-id (ZonedDateTime/now)))

(defn derive-password
  [password]
  (hashers/derive password))

(defn password-match?
  [password actual-password]
  (hashers/check password actual-password))

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
  ([user-id source-tweet-id]
   (->Retweet (str (UUID/randomUUID)) user-id false nil (ZonedDateTime/now) source-tweet-id))

  ([user-id source-tweet-id comment]
   (->Retweet (str (UUID/randomUUID)) user-id true comment (ZonedDateTime/now) source-tweet-id)))

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