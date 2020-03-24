(ns twitter-clj.application.service
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :as core]
            [twitter-clj.application.port.storage :as storage])
  (:import [java.util UUID]))

(defrecord Service [storage]
  component/Lifecycle
  (start [this]
    (log/info "Starting app")
    this)

  (stop [this]
    (log/info "Stopping app")
    this))

(defn make-service ;; Constructor.
  []
  (map->Service {}))

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

(defn- to-uuid
  [str]
  (UUID/fromString str))

(defn- find-any-user?
  [storage criteria]
  (not (empty? (storage/find-users! storage criteria))))

(defn- user-exists?
  [storage id]
  (find-any-user? storage {:id (to-uuid id)}))

(defn- new-user?
  [storage email]
  (not (find-any-user? storage {:email email})))

;; Public functions.

;; We can make it part of a protocol.

(defn add-user
  [app name email username]
  (let [user (core/new-user name email username)]
    (if (new-user? (:storage app) email)
      (storage/update-user! (:storage app) user)
      (throw-duplicate-user! email))))

(defn get-user-by-id
  [app user-id]
  (if-let [user (storage/fetch-user-by-id! (:storage app) user-id)]
    user
    (throw-missing-user! user-id)))

(defn add-tweet
  [app user-id text]
  (let [tweet (core/new-tweet user-id text)]
    (if (user-exists? (:storage app) user-id)
      (storage/update-tweet! (:storage app) tweet)
      (throw-missing-user! user-id))))

(defn get-tweet-by-id
  [app tweet-id]
  (if-let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)]
    (let [replies (storage/fetch-replies-by-tweet-id! (:storage app) tweet-id)]
      (assoc tweet :thread replies))
    (throw-missing-tweet! tweet-id)))

(defn get-tweets-by-user
  [app user-id]
  (if (user-exists? (:storage app) user-id)
    (storage/fetch-tweets-by-user! (:storage app) user-id)
    (throw-missing-user! user-id)))

(defn add-reply
  [app user-id text source-tweet-id]
  (if (user-exists? (:storage app) user-id)
    (if-let [source-tweet (storage/fetch-tweet-by-id! (:storage app) source-tweet-id)]
      (let [reply (core/new-tweet user-id text)]
        (storage/update-tweet! (:storage app) (core/reply source-tweet))
        (storage/update-replies! (:storage app) source-tweet-id reply)
        (storage/update-tweet! (:storage app) reply))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn retweet
  [app user-id source-tweet-id]
  (if (user-exists? (:storage app) user-id)
    (if-let [source-tweet (storage/fetch-tweet-by-id! (:storage app) source-tweet-id)]
      (let [updated-source-tweet (core/retweet source-tweet)
            retweet (core/new-retweet user-id updated-source-tweet)]
        (storage/update-tweet! (:storage app) updated-source-tweet)
        (storage/update-retweets! (:storage app) retweet))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn retweet-with-comment
  [app user-id comment source-tweet-id]
  (if (user-exists? (:storage app) user-id)
    (if-let [source-tweet (storage/fetch-tweet-by-id! (:storage app) source-tweet-id)]
      (let [updated-source-tweet (core/retweet source-tweet)
            retweet (core/new-retweet user-id updated-source-tweet comment)]
        (storage/update-tweet! (:storage app) updated-source-tweet)
        (storage/update-retweets! (:storage app) retweet))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn get-retweet-by-id
  [app retweet-id]
  (if-let [retweet (storage/fetch-retweet-by-id! (:storage app) retweet-id)]
    retweet
    (throw-missing-tweet! retweet-id)))

(defn get-retweets-by-tweet-id
  [app source-tweet-id]
  (if-let [retweets (storage/fetch-retweets-by-source-tweet-id! (:storage app) source-tweet-id)]
    retweets
    (throw-missing-tweet! source-tweet-id)))

(defn like
  [app user-id tweet-id]
  (if-let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)]
    (if (not (storage/find-like! (:storage app) user-id tweet-id))
      (do (storage/update-like! (:storage app) (core/new-like user-id tweet-id))
          (storage/update-tweet! (:storage app) (core/like tweet)))
      tweet)
    (throw-missing-tweet! tweet-id)))

(defn unlike
  [app user-id tweet-id]
  (if-let [tweet (storage/fetch-tweet-by-id! (:storage app) tweet-id)]
    (if (storage/find-like! (:storage app) user-id tweet-id)
      (do (storage/remove-like! (:storage app) user-id tweet-id)
          (storage/update-tweet! (:storage app) (core/unlike tweet)))
      tweet)
    (throw-missing-tweet! tweet-id)))

;(retweet [this user-id tweet-id])
;(reply [this reply-text source-tweet-id])