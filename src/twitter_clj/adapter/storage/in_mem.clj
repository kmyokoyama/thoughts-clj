(ns twitter-clj.adapter.storage.in-mem
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.port.storage :as storage])
  (:import [java.util UUID]))

(declare shutdown)

(defn- to-uuid
  [str]
  (UUID/fromString str))

;; Driven-side.

(defrecord InMemoryStorage [users tweets threads]
  component/Lifecycle
  (start [this]
    (log/info "Starting in-memory database")
    this)

  (stop [this]
    (log/info "Stopping in-memory database")
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
    (get @tweets (to-uuid tweet-id)))

  (fetch-user-by-id!
    [_ user-id]
    (get @users (to-uuid user-id)))

  (new-user?
    [_ email]
    (not-any? (fn [user] (= (:email user) email)) (vals @users))))

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