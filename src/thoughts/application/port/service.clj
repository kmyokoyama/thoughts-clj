(ns thoughts.application.port.service
  (:require [thoughts.application.port.protocol.service :as p]))

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

(defn thought
  [service user-id text]
  (p/thought service user-id text))

(defn get-thought-by-id
  [service thought-id]
  (p/get-thought-by-id service thought-id))

(defn get-thoughts-by-user
  [service user-id]
  (p/get-thoughts-by-user service user-id))

(defn get-thoughts-with-hashtag
  [service hashtag]
  (p/get-thoughts-with-hashtag service hashtag))

(defn reply
  [service user-id text source-thought-id]
  (p/reply service user-id text source-thought-id))

(defn get-replies-by-thought-id
  [service source-thought-id]
  (p/get-replies-by-thought-id service source-thought-id))

(defn rethought
  [service user-id source-thought-id]
  (p/rethought service user-id source-thought-id))

(defn rethought-with-comment
  [service user-id comment source-thought-id]
  (p/rethought-with-comment service user-id comment source-thought-id))

(defn get-rethought-by-id
  [service rethought-id]
  (p/get-rethought-by-id service rethought-id))

(defn get-rethoughts-by-thought-id
  [service source-thought-id]
  (p/get-rethoughts-by-thought-id service source-thought-id))

(defn like
  [service user-id thought-id]
  (p/like service user-id thought-id))

(defn unlike
  [service user-id thought-id]
  (p/unlike service user-id thought-id))

(defn get-feed
  [service user-id limit offset]
  (p/get-feed service user-id limit offset))