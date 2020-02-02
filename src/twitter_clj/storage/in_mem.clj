(ns twitter-clj.storage.in-mem
  (:require [twitter-clj.storage :as storage]))

(def users (atom {})) ;; It could also be a ref.
(def tweets (atom {})) ;; It could also be a ref.
(def threads (atom {})) ;; It could also be a ref.

;; Driven-side.

(deftype InMemoryStorage []
  storage/Storage
  (update-user!
    [_ {user-id :id :as user}]
    (swap! users (fn [users] (assoc users user-id user))))

  (update-tweet!
    [_ {tweet-id :id :as tweet}]
    (swap! tweets (fn [tweets] (assoc tweets tweet-id tweet))))

  (update-thread!
    [_ {thread-id :id :as thread}]
    (swap! threads (fn [threads] (assoc threads thread-id thread))))

  (fetch-thread-by-id!
    [_ thread-id]
    (get @threads thread-id))

  (inspect-users!
    [_]
    @users)

  (inspect-tweets!
    [_]
    @tweets)

  (inspect-threads!
    [_]
    @threads))