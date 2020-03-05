(ns twitter-clj.adapter.storage.in-mem
  (:require [twitter-clj.application.port.storage :as storage]
            [com.stuartsierra.component :as component]))

(def users (atom {})) ;; It could also be a ref.
(def tweets (atom {})) ;; It could also be a ref.
(def threads (atom {})) ;; It could also be a ref.

;; Driven-side.

(defrecord InMemoryStorage []
  component/Lifecycle
  (start [component]
    (println "Starting in-memory database")
    component)

  (stop [component]
    (println "Stopping in-memory database")
    component)

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

  (fetch-tweets-by-user!
    [_ user-id]
    (filter #(= (:user-id %) user-id) (vals @tweets)))

  (fetch-tweet-by-id!
    [_ tweet-id]
    (get @tweets tweet-id {}))

  (fetch-users!
    [_]
    @users)

  (fetch-tweets!
    [_]
    @tweets)

  (fetch-threads!
    [_]
    @threads)

  (shutdown!
    [_]
    (reset! users {})
    (reset! tweets {})
    (reset! threads {})))