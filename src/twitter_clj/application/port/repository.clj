(ns twitter-clj.application.port.repository
  (:require [twitter-clj.application.port.protocol.repository :as p]))

(defn update-user!
  [repository user]
  (p/update-user! repository user))

(defn fetch-users!
  [repository criteria]
  (p/fetch-users! repository criteria))

(defn update-password!
  [repository user-id password]
  (p/update-password! repository user-id password))

(defn fetch-password!
  [repository user-id]
  (p/fetch-password! repository user-id))

(defn update-follow!
  [repository follower followed]
  (p/update-follow! repository follower followed))

(defn fetch-following!
  [repository follower-id]
  (p/fetch-following! repository follower-id))

(defn fetch-followers!
  [repository followed-id]
  (p/fetch-followers! repository followed-id))

(defn remove-follow!
  [repository follower-id followed-id]
  (p/remove-follow! repository follower-id followed-id))

(defn update-tweet!
  [repository tweet hashtags]
  (p/update-tweet! repository tweet hashtags))

(defn fetch-tweets!
  [repository criteria]
  (p/fetch-tweets! repository criteria))

(defn update-like!
  [repository like]
  (p/update-like! repository like))

(defn fetch-likes!
  [repository criteria]
  (p/fetch-likes! repository criteria))

(defn remove-like!
  [repository criteria]
  (p/remove-like! repository criteria))

(defn update-reply!
  [repository source-tweet-id reply hashtags]
  (p/update-reply! repository source-tweet-id reply hashtags))

(defn fetch-replies!
  [repository criteria]
  (p/fetch-replies! repository criteria))

(defn update-retweet!
  [repository retweet hashtags]
  (p/update-retweet! repository retweet hashtags))

(defn fetch-retweets!
  [repository criteria]
  (p/fetch-retweets! repository criteria))
