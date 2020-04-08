(ns twitter-clj.application.service
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :as core]
            [twitter-clj.application.util :refer [highlight]]
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
  (throw (ex-info (str "User [" user-id "] not found")
                  {:type    :resource-not-found
                   :subject :user
                   :cause   (str "user with ID " user-id " not found")
                   :context {:user-id user-id}})))

(defn- throw-missing-tweet!
  [tweet-id]
  (throw (ex-info (str "Tweet [ID: " tweet-id "] not found")
                  {:type    :resource-not-found
                   :subject :tweet
                   :cause   (str "tweet with ID " tweet-id " not found")
                   :context {:tweet-id tweet-id}})))

(defn- throw-duplicate-user-email!
  [email]
  (throw (ex-info (str "User [email: " email "] already exists")
                  {:type    :duplicate-resource
                   :subject :user
                   :cause   (str "user with email '" email "' already exists")
                   :context {:attribute :email :email email}})))

(defn- throw-duplicate-username!
  [username]
  (throw (ex-info (str "User [username: " username "] already exists")
                  {:type    :duplicate-resource
                   :subject :user
                   :cause   (str "User with username '" username "' already exists")
                   :context {:attribute :username :username username}})))

(defn- throw-invalid-like!
  [tweet-id user-id]
  (throw (ex-info (str "Tweet [ID: " tweet-id "] already liked by user with ID" user-id)
                  {:type    :invalid-action
                   :subject :like
                   :cause   "you cannot like the same tweet more than once"
                   :context {:tweet-id tweet-id :user-id user-id}})))

(defn- throw-invalid-unlike!
  [tweet-id user-id]
  (throw (ex-info (str "Tweet [ID: " tweet-id "] has not been liked by user with ID yet " user-id)
                  {:type    :invalid-action
                   :subject :unlike
                   :cause   "you cannot unlike a tweet before like it"
                   :context {:tweet-id tweet-id :user-id user-id}})))

(defn user-exists?
  [repository id]
  (not (empty? (repository/fetch-users! repository id :by-id))))

(defn new-user?
  [repository email]
  (empty? (repository/fetch-users! repository {:email email} :by-fields)))

;; Public functions.

;; We can make it part of a protocol.

(defn password-match?
  [service user-id password]
  (let [actual-password (repository/fetch-password! (:repository service) user-id)]
    (core/password-match? password actual-password)))

(defn add-user
  [service name email username password]
  (highlight password)
  (let [lower-name (clojure.string/lower-case name)
        lower-email (clojure.string/lower-case email)
        lower-username (clojure.string/lower-case username)
        user (core/new-user lower-name lower-email lower-username)]
    (if (new-user? (:repository service) email)
      (if (empty? (repository/fetch-users! (:repository service) {:username username} :by-fields))
        (do (repository/update-password! (:repository service) (:id user) (core/derive-password password))
            (repository/update-user! (:repository service) user))
        (throw-duplicate-username! username))
      (throw-duplicate-user-email! email))))

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
    tweet
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

(defn get-replies-by-tweet-id
  [service source-tweet-id]
  (if-let [_tweet (repository/fetch-tweets! (:repository service) source-tweet-id :by-id)]
    (repository/fetch-replies! (:repository service) source-tweet-id :by-source-tweet-id)
    (throw-missing-tweet! source-tweet-id)))

(defn retweet
  [service user-id source-tweet-id]
  (if (user-exists? (:repository service) user-id)
    (if-let [source-tweet (repository/fetch-tweets! (:repository service) source-tweet-id :by-id)]
      (do (repository/update-tweet! (:repository service) (core/retweet source-tweet))
          (repository/update-retweets! (:repository service) (core/new-retweet user-id source-tweet-id)))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn retweet-with-comment
  [service user-id comment source-tweet-id]
  (if (user-exists? (:repository service) user-id)
    (if-let [source-tweet (repository/fetch-tweets! (:repository service) source-tweet-id :by-id)]
      (do (repository/update-tweet! (:repository service) (core/retweet source-tweet))
          (repository/update-retweets! (:repository service) (core/new-retweet user-id source-tweet-id comment)))
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
    (if-not (repository/fetch-likes! (:repository service) [tweet-id user-id] [:by-source-tweet-id :by-user-id])
      (do (repository/update-like! (:repository service) (core/new-like user-id tweet-id))
          (repository/update-tweet! (:repository service) (core/like tweet)))
      (throw-invalid-like! tweet-id user-id))
    (throw-missing-tweet! tweet-id)))

(defn unlike
  [service user-id tweet-id]
  (if-let [tweet (repository/fetch-tweets! (:repository service) tweet-id :by-id)]
    (if (repository/fetch-likes! (:repository service) [tweet-id user-id] [:by-source-tweet-id :by-user-id])
      (do (repository/remove-like! (:repository service) [tweet-id user-id] [:by-source-tweet-id :by-user-id])
          (repository/update-tweet! (:repository service) (core/unlike tweet)))
      (throw-invalid-unlike! tweet-id user-id))
    (throw-missing-tweet! tweet-id)))