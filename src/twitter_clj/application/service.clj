(ns twitter-clj.application.service
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :as core]
            [twitter-clj.application.port.repository :as repository])
  (:import [java.util UUID]))

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

(defn- user-exists?
  [repository id]
  (not (empty? (repository/fetch-users! repository id :by-id))))

(defn- new-user?
  [repository email]
  (empty? (repository/fetch-users! repository {:email email} :by-fields)))

;; Public functions.

;; We can make it part of a protocol.

(defn add-user
  [app name email username]
  (let [user (core/new-user name email username)]
    (if (new-user? (:repository app) email)
      (repository/update-user! (:repository app) user)
      (throw-duplicate-user! email))))

(defn get-user-by-id
  [app user-id]
  (if-let [user (repository/fetch-users! (:repository app) user-id :by-id)]
    user
    (throw-missing-user! user-id)))

(defn add-tweet
  [app user-id text]
  (let [tweet (core/new-tweet user-id text)]
    (if (user-exists? (:repository app) user-id)
      (repository/update-tweet! (:repository app) tweet)
      (throw-missing-user! user-id))))

(defn get-tweet-by-id
  [app tweet-id]
  (if-let [tweet (repository/fetch-tweets! (:repository app) tweet-id :by-id)]
    (let [replies (repository/fetch-replies! (:repository app) tweet-id :by-source-tweet-id)]
      (assoc tweet :thread replies))
    (throw-missing-tweet! tweet-id)))

(defn get-tweets-by-user
  [app user-id]
  (if (user-exists? (:repository app) user-id)
    (repository/fetch-tweets! (:repository app) user-id :by-user-id)
    (throw-missing-user! user-id)))

(defn add-reply
  [app user-id text source-tweet-id]
  (if (user-exists? (:repository app) user-id)
    (if-let [source-tweet (repository/fetch-tweets! (:repository app) source-tweet-id :by-id)]
      (let [reply (core/new-tweet user-id text)]
        (repository/update-tweet! (:repository app) (core/reply source-tweet))
        (repository/update-replies! (:repository app) source-tweet-id reply)
        (repository/update-tweet! (:repository app) reply))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn retweet
  [app user-id source-tweet-id]
  (if (user-exists? (:repository app) user-id)
    (if-let [source-tweet (repository/fetch-tweets! (:repository app) source-tweet-id :by-id)]
      (let [updated-source-tweet (core/retweet source-tweet)
            retweet (core/new-retweet user-id updated-source-tweet)]
        (repository/update-tweet! (:repository app) updated-source-tweet)
        (repository/update-retweets! (:repository app) retweet))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn retweet-with-comment
  [app user-id comment source-tweet-id]
  (if (user-exists? (:repository app) user-id)
    (if-let [source-tweet (repository/fetch-tweets! (:repository app) source-tweet-id :by-id)]
      (let [updated-source-tweet (core/retweet source-tweet)
            retweet (core/new-retweet user-id updated-source-tweet comment)]
        (repository/update-tweet! (:repository app) updated-source-tweet)
        (repository/update-retweets! (:repository app) retweet))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn get-retweet-by-id
  [app retweet-id]
  (if-let [retweet (repository/fetch-retweets! (:repository app) retweet-id :by-id)]
    retweet
    (throw-missing-tweet! retweet-id)))

(defn get-retweets-by-tweet-id
  [app source-tweet-id]
  (if-let [retweets (repository/fetch-retweets! (:repository app) source-tweet-id :by-source-tweet-id)]
    retweets
    (throw-missing-tweet! source-tweet-id)))

(defn like
  [app user-id tweet-id]
  (if-let [tweet (repository/fetch-tweets! (:repository app) tweet-id :by-id)]
    (if (not (repository/fetch-likes! (:repository app) [user-id tweet-id] :by-user-tweet-ids))
      (do (repository/update-like! (:repository app) (core/new-like user-id tweet-id))
          (repository/update-tweet! (:repository app) (core/like tweet))))
    (throw-missing-tweet! tweet-id)))

(defn unlike
  [app user-id tweet-id]
  (if-let [tweet (repository/fetch-tweets! (:repository app) tweet-id :by-id)]
    (if (repository/fetch-likes! (:repository app) [user-id tweet-id] :by-user-tweet-ids)
      (do (repository/remove-like! (:repository app) [user-id tweet-id] :by-user-tweet-ids)
          (repository/update-tweet! (:repository app) (core/unlike tweet))))
    (throw-missing-tweet! tweet-id)))