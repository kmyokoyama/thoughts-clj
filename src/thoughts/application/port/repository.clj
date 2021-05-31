(ns thoughts.application.port.repository
  (:require [thoughts.application.port.protocol.repository :as p]))

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

(defn update-thought!
  [repository thought hashtags]
  (p/update-thought! repository thought hashtags))

(defn fetch-thoughts!
  [repository criteria]
  (p/fetch-thoughts! repository criteria))

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
  [repository source-thought-id reply hashtags]
  (p/update-reply! repository source-thought-id reply hashtags))

(defn fetch-replies!
  [repository criteria]
  (p/fetch-replies! repository criteria))

(defn update-rethought!
  [repository rethought hashtags]
  (p/update-rethought! repository rethought hashtags))

(defn fetch-rethoughts!
  [repository criteria]
  (p/fetch-rethoughts! repository criteria))
