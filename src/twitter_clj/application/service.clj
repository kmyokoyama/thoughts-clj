(ns twitter-clj.application.service
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :as core]
            [twitter-clj.application.port.repository :as repository]))

(defrecord Service [repository]
  component/Lifecycle
  (start [this]
    (log/info "Starting service")
    this)

  (stop [this]
    (log/info "Stopping service")
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

(defn user-exists?
  [repository id]
  (not (empty? (repository/fetch-users! repository id :by-id))))

(defn new-user?
  [repository email]
  (empty? (repository/fetch-users! repository {:email email} :by-fields)))

;; Public functions.

;; We can make it part of a protocol.

(defn add-user
  [service name email username]
  (let [user (core/new-user name email username)]
    (if (new-user? (:repository service) email)
      (repository/update-user! (:repository service) user)
      (throw-duplicate-user! email))))

(defn get-user-by-id
  [service user-id]
  (if-let [user (repository/fetch-users! (:repository service) user-id :by-id)]
    user
    (throw-missing-user! user-id)))

(defn add-tweet
  [service user-id text]
  (let [tweet (core/new-tweet user-id text)]
    (if (user-exists? (:repository service) user-id)
      (repository/update-tweet! (:repository service) tweet)
      (throw-missing-user! user-id))))

(defn get-tweet-by-id
  [service tweet-id]
  (if-let [tweet (repository/fetch-tweets! (:repository service) tweet-id :by-id)]
    (let [replies (repository/fetch-replies! (:repository service) tweet-id :by-source-tweet-id)]
      (assoc tweet :thread replies))
    (throw-missing-tweet! tweet-id)))

(defn get-tweets-by-user
  [service user-id]
  (if (user-exists? (:repository service) user-id)
    (repository/fetch-tweets! (:repository service) user-id :by-user-id)
    (throw-missing-user! user-id)))

(defn add-reply
  [service user-id text source-tweet-id]
  (if (user-exists? (:repository service) user-id)
    (if-let [source-tweet (repository/fetch-tweets! (:repository service) source-tweet-id :by-id)]
      (let [reply (core/new-tweet user-id text)]
        (repository/update-tweet! (:repository service) (core/reply source-tweet))
        (repository/update-replies! (:repository service) source-tweet-id reply)
        (repository/update-tweet! (:repository service) reply))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn retweet
  [service user-id source-tweet-id]
  (if (user-exists? (:repository service) user-id)
    (if-let [source-tweet (repository/fetch-tweets! (:repository service) source-tweet-id :by-id)]
      (let [updated-source-tweet (core/retweet source-tweet)
            retweet (core/new-retweet user-id updated-source-tweet)]
        (repository/update-tweet! (:repository service) updated-source-tweet)
        (repository/update-retweets! (:repository service) retweet))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn retweet-with-comment
  [service user-id comment source-tweet-id]
  (if (user-exists? (:repository service) user-id)
    (if-let [source-tweet (repository/fetch-tweets! (:repository service) source-tweet-id :by-id)]
      (let [updated-source-tweet (core/retweet source-tweet)
            retweet (core/new-retweet user-id updated-source-tweet comment)]
        (repository/update-tweet! (:repository service) updated-source-tweet)
        (repository/update-retweets! (:repository service) retweet))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn get-retweet-by-id
  [service retweet-id]
  (if-let [retweet (repository/fetch-retweets! (:repository service) retweet-id :by-id)]
    retweet
    (throw-missing-tweet! retweet-id)))

(defn get-retweets-by-tweet-id
  [service source-tweet-id]
  (if-let [retweets (repository/fetch-retweets! (:repository service) source-tweet-id :by-source-tweet-id)]
    retweets
    (throw-missing-tweet! source-tweet-id)))

(defn like
  [service user-id tweet-id]
  (if-let [tweet (repository/fetch-tweets! (:repository service) tweet-id :by-id)]
    (if (not (repository/fetch-likes! (:repository service) [tweet-id user-id] [:by-source-tweet-id :by-user-id]))
      (do (repository/update-like! (:repository service) (core/new-like user-id tweet-id))
          (repository/update-tweet! (:repository service) (core/like tweet)))
      tweet)
    (throw-missing-tweet! tweet-id)))

(defn unlike
  [service user-id tweet-id]
  (if-let [tweet (repository/fetch-tweets! (:repository service) tweet-id :by-id)]
    (if (repository/fetch-likes! (:repository service) [tweet-id user-id] [:by-source-tweet-id :by-user-id])
      (do (repository/remove-like! (:repository service) [tweet-id user-id] [:by-source-tweet-id :by-user-id])
          (repository/update-tweet! (:repository service) (core/unlike tweet)))
      tweet)
    (throw-missing-tweet! tweet-id)))