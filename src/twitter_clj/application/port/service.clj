(ns twitter-clj.application.port.service
  (:require [twitter-clj.application.port.protocol.service :as p]))

(defn login
  [service user-id]
  (p/login service user-id))

(defn logout
  [service session-id]
  (p/logout service session-id))

(defn logout-all
  [service user-id]
  (p/logout-all service user-id))

(defn new-user?
  [service email]
  (p/new-user? service email))

(defn username-available?
  [service username]
  (p/username-available? service username))

(defn user-exists?
  [service user-id]
  (p/user-exists? service user-id))

(defn password-match?
  [service user-id password]
  (p/password-match? service user-id password))

(defn create-user
  [service name email username password]
  (p/create-user service name email username password))

(defn get-user-by-id
  [service user-id]
  (p/get-user-by-id service user-id))

(defn follow
  [service follower-id followed-id]
  (p/follow service follower-id followed-id))

(defn unfollow
  [service follower-id followed-id]
  (p/unfollow service follower-id followed-id))

(defn get-following
  [service follower-id]
  (p/get-following service follower-id))

(defn get-followers
  [service followed-id]
  (p/get-followers service followed-id))

(defn tweet
  [service user-id text]
  (p/tweet service user-id text))

(defn get-tweet-by-id
  [service tweet-id]
  (p/get-tweet-by-id service tweet-id))

(defn get-tweets-by-user
  [service user-id]
  (p/get-tweets-by-user service user-id))

(defn get-tweets-with-hashtag
  [service hashtag]
  (p/get-tweets-with-hashtag service hashtag))

(defn reply
  [service user-id text source-tweet-id]
  (p/reply service user-id text source-tweet-id))

(defn get-replies-by-tweet-id
  [service source-tweet-id]
  (p/get-replies-by-tweet-id service source-tweet-id))

(defn retweet
  [service user-id source-tweet-id]
  (p/retweet service user-id source-tweet-id))

(defn retweet-with-comment
  [service user-id comment source-tweet-id]
  (p/retweet-with-comment service user-id comment source-tweet-id))

(defn get-retweet-by-id
  [service retweet-id]
  (p/get-retweet-by-id service retweet-id))

(defn get-retweets-by-tweet-id
  [service source-tweet-id]
  (p/get-retweets-by-tweet-id service source-tweet-id))

(defn like
  [service user-id tweet-id]
  (p/like service user-id tweet-id))

(defn unlike
  [service user-id tweet-id]
  (p/unlike service user-id tweet-id))

(defn get-feed
  [service user-id limit offset]
  (p/get-feed service user-id limit offset))