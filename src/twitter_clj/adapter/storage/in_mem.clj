(ns twitter-clj.adapter.storage.in-mem
  (:require [twitter-clj.application.port.storage :as storage]
            [com.stuartsierra.component :as component]))

(declare shutdown)

;; Driven-side.

(defrecord InMemoryStorage [users tweets threads]
  component/Lifecycle
  (start [this]
    (println "Starting in-memory database")
    this)

  (stop [this]
    (println "Stopping in-memory database")
    (shutdown this))

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
    @threads))

(defn make-in-mem-storage ;; Constructor.
  []
  (map->InMemoryStorage {:users (atom {})
                         :tweets (atom {})
                         :threads (atom {})}))

(defn shutdown
  [storage]
  (reset! (:users storage) {})
  (reset! (:tweets storage) {})
  (reset! (:threads storage) {})
  storage)