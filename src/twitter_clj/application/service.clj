(ns twitter-clj.application.service
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :as core]
            [twitter-clj.application.port.repository :as repository]))

(defrecord Service [repository]
  component/Lifecycle
  (start [this]
    (log/info "Starting application service")
    this)

  (stop [this]
    (log/info "Stopping application service")
    this))

(defn make-service                                          ;; Constructor.
  []
  (map->Service {}))

;; Private functions.

(defn- throw-missing-user!
  [user-id]
  (throw (ex-info (str "User [ID: " user-id "] not found")
                  {:type    :resource-not-found
                   :subject :user
                   :cause   (str "user with ID '" user-id "' not found")
                   :context {:user-id user-id}})))

(defn- throw-missing-tweet!
  [tweet-id]
  (throw (ex-info (str "Tweet [ID: " tweet-id "] not found")
                  {:type    :resource-not-found
                   :subject :tweet
                   :cause   (str "tweet with ID '" tweet-id "' not found")
                   :context {:tweet-id tweet-id}})))

(defn- throw-missing-retweet!
  [tweet-id]
  (throw (ex-info (str "Retweet [ID: " tweet-id "] not found")
                  {:type    :resource-not-found
                   :subject :retweet
                   :cause   (str "retweet with ID '" tweet-id "' not found")
                   :context {:retweet-id tweet-id}})))

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
  (throw (ex-info (str "User [ID: " user-id "] already likes Tweet [ID: " tweet-id "]")
                  {:type    :invalid-action
                   :subject :like
                   :cause   "you cannot like the same tweet more than once"
                   :context {:tweet-id tweet-id :user-id user-id}})))

(defn- throw-invalid-unlike!
  [tweet-id user-id]
  (throw (ex-info (str "Tweet [ID: " tweet-id "] has not been liked by User [ID: " user-id "] yet")
                  {:type    :invalid-action
                   :subject :unlike
                   :cause   "you cannot unlike a tweet you do not like yet"
                   :context {:tweet-id tweet-id :user-id user-id}})))

(defn- throw-invalid-follow!
  [cause follower-id followed-id]
  (case cause
    :follow-yourself (throw (ex-info (str "User [ID: " follower-id "] cannot be followed by him/herself")
                                     {:type    :invalid-action
                                      :subject :follow
                                      :cause   "you cannot follow yourself"
                                      :context {:follower-id follower-id :followed-id follower-id}}))
    :already-following (throw (ex-info (str "User [ID: " follower-id "] already follows User [ID: " followed-id "]")
                                       {:type    :invalid-action
                                        :subject :follow
                                        :cause   "you cannot follow the same user more than once"
                                        :context {:follower-id follower-id :followed-id followed-id}}))))

(defn- throw-invalid-unfollow!
  [cause follower-id followed-id]
  (case cause
    :unfollow-yourself (throw (ex-info (str "User [ID: " follower-id "] cannot be unfollowed by her/himself")
                                       {:type    :invalid-action
                                        :subject :unfollow
                                        :cause   "you cannot unfollow yourself"
                                        :context {:follower-id follower-id :followed-id follower-id}}))
    :not-following-yet (throw (ex-info (str "User [ID: " follower-id "] does not follow User [ID: " followed-id "] yet")
                                       {:type    :invalid-action
                                        :subject :unfollow
                                        :cause   "you cannot unfollow an user you do not follow yet"
                                        :context {:follower-id follower-id :followed-id followed-id}}))))

;; Public functions.

;; We can make it part of a protocol.

(defn- following?
  [service follower-id followed-id]
  (some #(= followed-id (:id %)) (repository/fetch-following! (:repository service) follower-id)))

(defn new-user?
  [service email]
  (empty? (repository/fetch-users! (:repository service) {:email email})))

(defn user-exists?
  [service id]
  (not (empty? (repository/fetch-users! (:repository service) {:id id}))))

(defn password-match?
  [service user-id password]
  (let [actual-password (repository/fetch-password! (:repository service) user-id)]
    (core/password-match? password actual-password)))

(defn logged-in?
  [service user-id]
  ((complement empty?) (repository/fetch-sessions! (:repository service) {:user-id user-id})))

(defn login
  [service user-id]
  (repository/update-session! (:repository service) (core/new-session user-id)))

(defn logout
  [service user-id]
  (repository/remove-session! (:repository service) {:user-id user-id}))

(defn add-user
  [service name email username password]
  (let [lower-name (clojure.string/lower-case name)
        lower-email (clojure.string/lower-case email)
        lower-username (clojure.string/lower-case username)
        user (core/new-user lower-name lower-email lower-username)]
    (if (new-user? service email)
      (if (empty? (repository/fetch-users! (:repository service) {:username username}))
        (do (repository/update-user! (:repository service) user)
            (repository/update-password! (:repository service) (:id user) (core/derive-password password))
            user)
        (throw-duplicate-username! username))
      (throw-duplicate-user-email! email))))

(defn get-user-by-id
  [service user-id]
  (if-let [user (first (repository/fetch-users! (:repository service) {:id user-id}))]
    user
    (throw-missing-user! user-id)))

(defn add-tweet
  [service user-id text]
  (let [tweet (core/new-tweet user-id text)]
    (if (user-exists? service user-id)
      (repository/update-tweet! (:repository service) tweet)
      (throw-missing-user! user-id))))

(defn get-tweet-by-id
  [service tweet-id]
  (if-let [tweet (first (repository/fetch-tweets! (:repository service) {:id tweet-id}))]
    tweet
    (throw-missing-tweet! tweet-id)))

(defn get-tweets-by-user
  [service user-id]
  (if (user-exists? service user-id)
    (repository/fetch-tweets! (:repository service) {:user-id user-id})
    (throw-missing-user! user-id)))

(defn add-reply
  [service user-id text source-tweet-id]
  (if (user-exists? service user-id)
    (if-let [source-tweet (first (repository/fetch-tweets! (:repository service) {:id source-tweet-id}))]
      (let [reply (core/new-tweet user-id text)]
        (repository/update-tweet! (:repository service) (core/reply source-tweet))
        (repository/update-reply! (:repository service) source-tweet-id reply)
        (repository/update-tweet! (:repository service) reply))
      (throw-missing-tweet! source-tweet-id))
    (throw-missing-user! user-id)))

(defn get-replies-by-tweet-id
  [service source-tweet-id]
  (if-not (empty? (repository/fetch-tweets! (:repository service) {:id source-tweet-id}))
    (repository/fetch-replies! (:repository service) {:source-tweet-id source-tweet-id})
    (throw-missing-tweet! source-tweet-id)))

(defn retweet
  [service user-id source-tweet-id]
  (if (user-exists? service user-id)
    (if-let [source-tweet (first (repository/fetch-tweets! (:repository service) {:id source-tweet-id}))]
      (do (repository/update-tweet! (:repository service) (core/retweet source-tweet))
          (repository/update-retweet! (:repository service) (core/new-retweet user-id source-tweet-id)))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn retweet-with-comment
  [service user-id comment source-tweet-id]
  (if (user-exists? service user-id)
    (if-let [source-tweet (first (repository/fetch-tweets! (:repository service) {:id source-tweet-id}))]
      (do (repository/update-tweet! (:repository service) (core/retweet source-tweet))
          (repository/update-retweet! (:repository service) (core/new-retweet user-id source-tweet-id comment)))
      (throw-missing-user! source-tweet-id))
    (throw-missing-user! user-id)))

(defn get-retweet-by-id
  [service retweet-id]
  (if-let [retweet (first (repository/fetch-retweets! (:repository service) {:id retweet-id}))]
    retweet
    (throw-missing-retweet! retweet-id)))

(defn get-retweets-by-tweet-id
  [service source-tweet-id]
  (if-let [retweets (repository/fetch-retweets! (:repository service) {:source-tweet-id source-tweet-id})]
    retweets
    (throw-missing-tweet! source-tweet-id)))

(defn like
  [service user-id tweet-id]
  (if-let [tweet (first (repository/fetch-tweets! (:repository service) {:id tweet-id}))]
    (if (empty? (repository/fetch-likes! (:repository service) {:user-id user-id :source-tweet-id tweet-id}))
      (do (repository/update-like! (:repository service) (core/new-like user-id tweet-id))
          (repository/update-tweet! (:repository service) (core/like tweet)))
      (throw-invalid-like! tweet-id user-id))
    (throw-missing-tweet! tweet-id)))

(defn unlike
  [service user-id tweet-id]
  (if-let [tweet (first (repository/fetch-tweets! (:repository service) {:id tweet-id}))]
    (if-not (empty? (repository/fetch-likes! (:repository service) {:user-id user-id :source-tweet-id tweet-id}))
      (do (repository/remove-like! (:repository service) {:user-id user-id :source-tweet-id tweet-id})
          (repository/update-tweet! (:repository service) (core/unlike tweet)))
      (throw-invalid-unlike! tweet-id user-id))
    (throw-missing-tweet! tweet-id)))

(defn follow
  [service follower-id followed-id]
  (if (user-exists? service follower-id)
    (if (user-exists? service followed-id)
      (if-not (= follower-id followed-id)
        (if-not (following? service follower-id followed-id)
          (let [follower (first (repository/fetch-users! (:repository service) {:id follower-id}))
                followed (first (repository/fetch-users! (:repository service) {:id followed-id}))
                [updated-follower updated-followed] (core/follow follower followed)]
            (do (repository/update-user! (:repository service) updated-follower)
                (repository/update-user! (:repository service) updated-followed)
                (repository/update-follow! (:repository service) updated-follower updated-followed)
                updated-followed))
          (throw-invalid-follow! :already-following follower-id followed-id))
        (throw-invalid-follow! :follow-yourself follower-id followed-id))
      (throw-missing-user! followed-id))
    (throw-missing-user! follower-id)))

(defn unfollow
  [service follower-id followed-id]
  (if (user-exists? service follower-id)
    (if (user-exists? service followed-id)
      (if-not (= follower-id followed-id)
        (if (following? service follower-id followed-id)
          (let [follower (first (repository/fetch-users! (:repository service) {:id follower-id}))
                followed (first (repository/fetch-users! (:repository service) {:id followed-id}))
                [updated-follower updated-followed] (core/unfollow follower followed)]
            (do (repository/update-user! (:repository service) updated-follower)
                (repository/update-user! (:repository service) updated-followed)
                (repository/remove-follow! (:repository service) updated-follower updated-followed)
                updated-followed))
          (throw-invalid-unfollow! :not-following-yet follower-id followed-id))
        (throw-invalid-unfollow! :unfollow-yourself follower-id followed-id))
      (throw-missing-user! followed-id))
    (throw-missing-user! follower-id)))

(defn get-following
  [service follower-id]
  (if (user-exists? service follower-id)
    (repository/fetch-following! (:repository service) follower-id)
    (throw-missing-user! follower-id)))

(defn get-followers
  [service followed-id]
  (if (user-exists? service followed-id)
    (repository/fetch-followers! (:repository service) followed-id)
    (throw-missing-user! followed-id)))

(defn get-feed
  [service user-id limit]
  (if (user-exists? service user-id)
    (let [following (repository/fetch-following! (:repository service) user-id)]
      (->> following
           (map :id)
           (map (fn [user-id] {:user-id user-id}))
           (map (fn [user-id-criteria] (repository/fetch-tweets! (:repository service) user-id-criteria)))
           (map (fn [user-tweets] (core/sort-by-date user-tweets)))
           (map (fn [sorted-user-tweets] (take 10 sorted-user-tweets)))
           (flatten)
           (core/sort-by-date)
           (take limit)))
    (throw-missing-user! user-id)))