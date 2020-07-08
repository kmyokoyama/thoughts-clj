(ns twitter-clj.application.service
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :as core]
            [twitter-clj.application.port.cache :as cache]
            [twitter-clj.application.port.repository :as repository]
            [twitter-clj.application.port.service :as service]
            [twitter-clj.application.port.protocol.service :as p]))

(declare throw-missing-user!)
(declare throw-missing-tweet!)
(declare throw-missing-retweet!)
(declare throw-duplicate-user-email!)
(declare throw-duplicate-username!)
(declare throw-invalid-like!)
(declare throw-invalid-unlike!)
(declare throw-invalid-follow!)
(declare throw-invalid-unfollow!)
(declare following?)
(declare build-feed)
(declare map->Service)

(defn make-service                                          ;; Constructor.
  []
  (map->Service {}))

(defrecord Service [repository cache]
  component/Lifecycle
  (start [this]
    (log/info "Starting application service")
    this)

  (stop [this]
    (log/info "Stopping application service")
    this)

  p/UserService
  (login
    [service user-id]
    (let [session (core/new-session user-id)]
      (cache/update-session! (:cache service) session)
      (:id session)))

  (logout
    [service session-id]
    (cache/remove-session! (:cache service) {:session-id session-id}))

  (logout-all
    [service user-id]
    (cache/remove-session! (:cache service) {:user-id user-id}))

  (new-user?
    [service email]
    (empty? (repository/fetch-users! (:repository service) {:email email})))

  (user-exists?
    [service id]
    (not (empty? (repository/fetch-users! (:repository service) {:id id}))))

  (username-available?
    [service username]
    (empty? (repository/fetch-users! (:repository service) {:username username})))

  (password-match?
    [service user-id password]
    (let [actual-password (repository/fetch-password! (:repository service) user-id)]
      (core/password-match? password actual-password)))

  (create-user
    [service name email username password]
    (let [lower-name (clojure.string/lower-case name) ;; XXX: Should we really make it lowercase?
          lower-email (clojure.string/lower-case email)
          lower-username (clojure.string/lower-case username)
          user (core/new-user lower-name lower-email lower-username)]
      (if (service/new-user? service email)
        (if (service/username-available? service username)
          (do (repository/update-user! (:repository service) user)
              (repository/update-password! (:repository service) (:id user) (core/derive-password password))
              user)
          (throw-duplicate-username! username))
        (throw-duplicate-user-email! email))))

  (get-user-by-id
    [service user-id]
    (if-let [user (first (repository/fetch-users! (:repository service) {:id user-id}))]
      user
      (throw-missing-user! user-id)))

  (follow
    [service follower-id followed-id]
    (if (service/user-exists? service follower-id)
      (if (service/user-exists? service followed-id)
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

  (unfollow
    [service follower-id followed-id]
    (if (service/user-exists? service follower-id)
      (if (service/user-exists? service followed-id)
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

  (get-following
    [service follower-id]
    (if (service/user-exists? service follower-id)
      (repository/fetch-following! (:repository service) follower-id)
      (throw-missing-user! follower-id)))

  (get-followers
    [service followed-id]
    (if (service/user-exists? service followed-id)
      (repository/fetch-followers! (:repository service) followed-id)
      (throw-missing-user! followed-id)))

  p/TweetService
  (tweet
    [service user-id text]
    (let [tweet (core/new-tweet user-id text)]
      (if (service/user-exists? service user-id)
        (repository/update-tweet! (:repository service) tweet (core/extract-hashtags (:text tweet)))
        (throw-missing-user! user-id))))

  (get-tweet-by-id
    [service tweet-id]
    (if-let [tweet (first (repository/fetch-tweets! (:repository service) {:id tweet-id}))]
      tweet
      (throw-missing-tweet! tweet-id)))

  (get-tweets-by-user
    [service user-id]
    (if (service/user-exists? service user-id)
      (repository/fetch-tweets! (:repository service) {:user-id user-id})
      (throw-missing-user! user-id)))

  (get-tweets-with-hashtag
    [service hashtag]
    (repository/fetch-tweets! (:repository service) {:hashtag hashtag}))

  (reply
    [service user-id text source-tweet-id]
    (if (service/user-exists? service user-id)
      (if-let [source-tweet (first (repository/fetch-tweets! (:repository service) {:id source-tweet-id}))]
        (let [reply (core/new-tweet user-id text)]
          (repository/update-tweet! (:repository service) (core/reply source-tweet) #{})
          (repository/update-reply! (:repository service) source-tweet-id reply (core/extract-hashtags (:text reply))))
        (throw-missing-tweet! source-tweet-id))
      (throw-missing-user! user-id)))

  (get-replies-by-tweet-id
    [service source-tweet-id]
    (if-not (empty? (repository/fetch-tweets! (:repository service) {:id source-tweet-id}))
      (repository/fetch-replies! (:repository service) {:source-tweet-id source-tweet-id})
      (throw-missing-tweet! source-tweet-id)))

  (retweet
    [service user-id source-tweet-id]
    (if (service/user-exists? service user-id)
      (if-let [source-tweet (first (repository/fetch-tweets! (:repository service) {:id source-tweet-id}))]
        (do (repository/update-tweet! (:repository service) (core/retweet source-tweet) #{})
            (repository/update-retweet! (:repository service) (core/new-retweet user-id source-tweet-id) #{}))
        (throw-missing-user! source-tweet-id))
      (throw-missing-user! user-id)))

  (retweet-with-comment
    [service user-id comment source-tweet-id]
    (if (service/user-exists? service user-id)
      (if-let [source-tweet (first (repository/fetch-tweets! (:repository service) {:id source-tweet-id}))]
        (do (repository/update-tweet! (:repository service) (core/retweet source-tweet) #{})
            (repository/update-retweet! (:repository service) (core/new-retweet user-id source-tweet-id comment) (core/extract-hashtags comment)))
        (throw-missing-user! source-tweet-id))
      (throw-missing-user! user-id)))

  (get-retweet-by-id
    [service retweet-id]
    (if-let [retweet (first (repository/fetch-retweets! (:repository service) {:id retweet-id}))]
      retweet
      (throw-missing-retweet! retweet-id)))

  (get-retweets-by-tweet-id
    [service source-tweet-id]
    (if-let [retweets (repository/fetch-retweets! (:repository service) {:source-tweet-id source-tweet-id})]
      retweets
      (throw-missing-tweet! source-tweet-id)))

  (like
    [service user-id tweet-id]
    (if-let [tweet (first (repository/fetch-tweets! (:repository service) {:id tweet-id}))]
      (if (empty? (repository/fetch-likes! (:repository service) {:user-id user-id :source-tweet-id tweet-id}))
        (do (repository/update-like! (:repository service) (core/new-like user-id tweet-id))
            (repository/update-tweet! (:repository service) (core/like tweet) #{}))
        (throw-invalid-like! tweet-id user-id))
      (throw-missing-tweet! tweet-id)))

  (unlike
    [service user-id tweet-id]
    (if-let [tweet (first (repository/fetch-tweets! (:repository service) {:id tweet-id}))]
      (if-not (empty? (repository/fetch-likes! (:repository service) {:user-id user-id :source-tweet-id tweet-id}))
        (do (repository/remove-like! (:repository service) {:user-id user-id :source-tweet-id tweet-id})
            (repository/update-tweet! (:repository service) (core/unlike tweet) #{}))
        (throw-invalid-unlike! tweet-id user-id))
      (throw-missing-tweet! tweet-id)))

  (get-feed
    [service user-id limit offset]
    (if (service/user-exists? service user-id)
      (let [feed-cache (cache/fetch-feed! (:cache service) user-id limit offset)]
        (if-not (empty? feed-cache)
          feed-cache
          (let [following (repository/fetch-following! (:repository service) user-id)
                feed (build-feed service following)]
            (if-not (empty? feed)
              (do (cache/update-feed! (:cache service) user-id feed 360)
                  (cache/fetch-feed! (:cache service) user-id limit offset))
              feed))))                                      ;; TTL of 5 minutes.
      (throw-missing-user! user-id))))

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

(defn- following?
  "Is `follower-id` following `followed-id`?

  Both `follower-id` and `followed-id` are user identifiers."
  [service follower-id followed-id]
  (some #(= followed-id (:id %)) (repository/fetch-following! (:repository service) follower-id)))

(defn- build-feed
  "Creates a collection of tweets (length <= 100) from users in `following` sorted by `:publish-date`."
  [service following]
  (->> following
       (map :id)
       (map (fn [user-id] {:user-id user-id}))
       (map (fn [user-id-criteria] (repository/fetch-tweets! (:repository service) user-id-criteria)))
       (map (fn [user-tweets] (core/sort-by-date user-tweets)))
       (core/merge-by-date 100)))