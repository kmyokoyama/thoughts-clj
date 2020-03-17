(ns twitter-clj.application.app
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :as core]
            [twitter-clj.application.port.storage :as storage]
            [twitter-clj.application.util :refer [success error process]]))

(defrecord App [storage]
  component/Lifecycle
  (start [this]
    (log/info "Starting app")
    this)

  (stop [this]
    (log/info "Stopping app")
    this))

(defn make-app ;; Constructor.
  []
  (map->App {}))

;; Private functions.

(defn- throw-missing-user!
  [user-id]
  (throw (ex-info "User not found" {:type :resource-not-found
                                    :resource-type :user
                                    :resource-id user-id})))

(defn- throw-missing-tweet!
  [tweet-id]
  (throw (ex-info "Tweet not found" {:type :resource-not-found
                                     :resource-type :tweet
                                     :resource-id tweet-id})))

(defn- throw-duplicate-user!
  [key]
  (throw (ex-info "User already exists" {:type :duplicate-resource
                                         :resource-type :user
                                         :resource-key key})))

;; Public functions.

;; We can make it part of a protocol.

(defn add-user
  [app name email username]
  (let [user (core/new-user name email username)]
    (if (storage/new-user? (:storage app) email)
      (core/update-user! user (:storage app))
      (throw-duplicate-user! email))))

(defn get-user-by-id
  [app user-id]
  (if-let [user (storage/fetch-user-by-id! (:storage app) user-id)]
    user
    (throw-missing-user! user-id)))

(defn add-tweet
  [app user-id text] ;; TODO: Handle the case in which there is no user with this user-id.
  (let [{:keys [tweet thread] :as tweet-thread} (core/new-tweet user-id text)]
    (core/update-tweet! tweet (:storage app))
    (core/update-thread! thread (:storage app))
    tweet-thread))

(defn get-tweet-by-id
  [app tweet-id]
  (if-let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)]
    tweet
    (throw-missing-tweet! tweet-id)))

(defn get-tweets-by-user
  [app user-id] ;; TODO: Handle the case in which there is no user with this user-id.
  (storage/fetch-tweets-by-user! (:storage app) user-id))

(defn like
  [app tweet-id]
  (if-let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)]
    (storage/update-tweet! (:storage app) (core/like tweet))
    (throw-missing-tweet! tweet-id)))

(defn unlike
  [app tweet-id]
  (if-let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)]
    (storage/update-tweet! (:storage app) (core/unlike tweet))
    (throw-missing-tweet! tweet-id)))

;(retweet [this user-id tweet-id])
;(reply [this reply-text source-tweet-id])