(ns thoughts.application.service
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [thoughts.application.core :as core]
            [thoughts.port.repository :as p.repository]
            [thoughts.port.service :as p.service]
            [thoughts.port.cache :as p.cache]))

(declare throw-missing-user!)
(declare throw-missing-thought!)
(declare throw-missing-rethought!)
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

  p.service/UserService
  (login
    [service user-id]
    (let [session (core/new-session user-id)]
      (p.cache/update-session! (:cache service) session)
      (:id session)))

  (logout
    [service session-id]
    (p.cache/remove-session! (:cache service) {:session-id session-id}))

  (logout-all
    [service user-id]
    (p.cache/remove-session! (:cache service) {:user-id user-id}))

  (new-user?
    [service email]
    (empty? (p.repository/fetch-users! (:repository service) {:email email})))

  (user-exists?
    [service id]
    (not (empty? (p.repository/fetch-users! (:repository service) {:id id}))))

  (username-available?
    [service username]
    (empty? (p.repository/fetch-users! (:repository service) {:username username})))

  (password-match?
    [service user-id password]
    (let [actual-password (p.repository/fetch-password! (:repository service) user-id)]
      (core/password-match? password actual-password)))

  (create-user
    [service name email username password]
    (let [lower-name (clojure.string/lower-case name)       ;; XXX: Should we really make it lowercase?
          lower-email (clojure.string/lower-case email)
          lower-username (clojure.string/lower-case username)
          user (core/new-user lower-name lower-email lower-username)]
      (if (p.service/new-user? service email)
        (if (p.service/username-available? service username)
          (do (p.repository/update-user! (:repository service) user)
              (p.repository/update-password! (:repository service) (:id user) (core/derive-password password))
              user)
          (throw-duplicate-username! username))
        (throw-duplicate-user-email! email))))

  (get-user-by-id
    [service user-id]
    (if-let [user (first (p.repository/fetch-users! (:repository service) {:id user-id}))]
      user
      (throw-missing-user! user-id)))

  (follow
    [service follower-id followed-id]
    (if (p.service/user-exists? service follower-id)
      (if (p.service/user-exists? service followed-id)
        (if-not (= follower-id followed-id)
          (if-not (following? service follower-id followed-id)
            (let [follower (first (p.repository/fetch-users! (:repository service) {:id follower-id}))
                  followed (first (p.repository/fetch-users! (:repository service) {:id followed-id}))
                  [updated-follower updated-followed] (core/follow follower followed)]
              (do (p.repository/update-user! (:repository service) updated-follower)
                  (p.repository/update-user! (:repository service) updated-followed)
                  (p.repository/update-follow! (:repository service) updated-follower updated-followed)
                  updated-followed))
            (throw-invalid-follow! :already-following follower-id followed-id))
          (throw-invalid-follow! :follow-yourself follower-id followed-id))
        (throw-missing-user! followed-id))
      (throw-missing-user! follower-id)))

  (unfollow
    [service follower-id followed-id]
    (if (p.service/user-exists? service follower-id)
      (if (p.service/user-exists? service followed-id)
        (if-not (= follower-id followed-id)
          (if (following? service follower-id followed-id)
            (let [follower (first (p.repository/fetch-users! (:repository service) {:id follower-id}))
                  followed (first (p.repository/fetch-users! (:repository service) {:id followed-id}))
                  [updated-follower updated-followed] (core/unfollow follower followed)]
              (do (p.repository/update-user! (:repository service) updated-follower)
                  (p.repository/update-user! (:repository service) updated-followed)
                  (p.repository/remove-follow! (:repository service) updated-follower updated-followed)
                  updated-followed))
            (throw-invalid-unfollow! :not-following-yet follower-id followed-id))
          (throw-invalid-unfollow! :unfollow-yourself follower-id followed-id))
        (throw-missing-user! followed-id))
      (throw-missing-user! follower-id)))

  (get-following
    [service follower-id]
    (if (p.service/user-exists? service follower-id)
      (p.repository/fetch-following! (:repository service) follower-id)
      (throw-missing-user! follower-id)))

  (get-followers
    [service followed-id]
    (if (p.service/user-exists? service followed-id)
      (p.repository/fetch-followers! (:repository service) followed-id)
      (throw-missing-user! followed-id)))

  p.service/ThoughtService
  (thought
    [service user-id text]
    (let [thought (core/new-thought user-id text)]
      (if (p.service/user-exists? service user-id)
        (p.repository/update-thought! (:repository service) thought (core/extract-hashtags (:text thought)))
        (throw-missing-user! user-id))))

  (get-thought-by-id
    [service thought-id]
    (if-let [thought (first (p.repository/fetch-thoughts! (:repository service) {:id thought-id}))]
      thought
      (throw-missing-thought! thought-id)))

  (get-thoughts-by-user
    [service user-id]
    (if (p.service/user-exists? service user-id)
      (p.repository/fetch-thoughts! (:repository service) {:user-id user-id})
      (throw-missing-user! user-id)))

  (get-thoughts-with-hashtag
    [service hashtag]
    (p.repository/fetch-thoughts! (:repository service) {:hashtag hashtag}))

  (reply
    [service user-id text source-thought-id]
    (if (p.service/user-exists? service user-id)
      (if-let [source-thought (first (p.repository/fetch-thoughts! (:repository service) {:id source-thought-id}))]
        (let [reply (core/new-thought user-id text)]
          (p.repository/update-thought! (:repository service) (core/reply source-thought) #{})
          (p.repository/update-reply! (:repository service) source-thought-id reply (core/extract-hashtags (:text reply))))
        (throw-missing-thought! source-thought-id))
      (throw-missing-user! user-id)))

  (get-replies-by-thought-id
    [service source-thought-id]
    (if-not (empty? (p.repository/fetch-thoughts! (:repository service) {:id source-thought-id}))
      (p.repository/fetch-replies! (:repository service) {:source-thought-id source-thought-id})
      (throw-missing-thought! source-thought-id)))

  (rethought
    [service user-id source-thought-id]
    (if (p.service/user-exists? service user-id)
      (if-let [source-thought (first (p.repository/fetch-thoughts! (:repository service) {:id source-thought-id}))]
        (do (p.repository/update-thought! (:repository service) (core/rethought source-thought) #{})
            (p.repository/update-rethought! (:repository service) (core/new-rethought user-id source-thought-id) #{}))
        (throw-missing-user! source-thought-id))
      (throw-missing-user! user-id)))

  (rethought-with-comment
    [service user-id comment source-thought-id]
    (if (p.service/user-exists? service user-id)
      (if-let [source-thought (first (p.repository/fetch-thoughts! (:repository service) {:id source-thought-id}))]
        (do (p.repository/update-thought! (:repository service) (core/rethought source-thought) #{})
            (p.repository/update-rethought! (:repository service) (core/new-rethought user-id source-thought-id comment) (core/extract-hashtags comment)))
        (throw-missing-user! source-thought-id))
      (throw-missing-user! user-id)))

  (get-rethought-by-id
    [service rethought-id]
    (if-let [rethought (first (p.repository/fetch-rethoughts! (:repository service) {:id rethought-id}))]
      rethought
      (throw-missing-rethought! rethought-id)))

  (get-rethoughts-by-thought-id
    [service source-thought-id]
    (if-let [rethoughts (p.repository/fetch-rethoughts! (:repository service) {:source-thought-id source-thought-id})]
      rethoughts
      (throw-missing-thought! source-thought-id)))

  (like
    [service user-id thought-id]
    (if-let [thought (first (p.repository/fetch-thoughts! (:repository service) {:id thought-id}))]
      (if (empty? (p.repository/fetch-likes! (:repository service) {:user-id user-id :source-thought-id thought-id}))
        (do (p.repository/update-like! (:repository service) (core/new-like user-id thought-id))
            (p.repository/update-thought! (:repository service) (core/like thought) #{}))
        (throw-invalid-like! thought-id user-id))
      (throw-missing-thought! thought-id)))

  (unlike
    [service user-id thought-id]
    (if-let [thought (first (p.repository/fetch-thoughts! (:repository service) {:id thought-id}))]
      (if-not (empty? (p.repository/fetch-likes! (:repository service) {:user-id user-id :source-thought-id thought-id}))
        (do (p.repository/remove-like! (:repository service) {:user-id user-id :source-thought-id thought-id})
            (p.repository/update-thought! (:repository service) (core/unlike thought) #{}))
        (throw-invalid-unlike! thought-id user-id))
      (throw-missing-thought! thought-id)))

  (get-feed
    [service user-id limit offset]
    (if (p.service/user-exists? service user-id)
      (let [feed-cache (p.cache/fetch-feed! (:cache service) user-id limit offset)]
        (if-not (empty? feed-cache)
          feed-cache
          (let [following (p.repository/fetch-following! (:repository service) user-id)
                feed (build-feed service following)]
            (if-not (empty? feed)
              (do (p.cache/update-feed! (:cache service) user-id feed 360)
                  (p.cache/fetch-feed! (:cache service) user-id limit offset))
              feed))))                                      ;; TTL of 5 minutes.
      (throw-missing-user! user-id))))

(defn- throw-missing-user!
  [user-id]
  (throw (ex-info (str "User [ID: " user-id "] not found")
                  {:type    :resource-not-found
                   :subject :user
                   :cause   (str "user with ID '" user-id "' not found")
                   :context {:user-id user-id}})))

(defn- throw-missing-thought!
  [thought-id]
  (throw (ex-info (str "Thought [ID: " thought-id "] not found")
                  {:type    :resource-not-found
                   :subject :thought
                   :cause   (str "thought with ID '" thought-id "' not found")
                   :context {:thought-id thought-id}})))

(defn- throw-missing-rethought!
  [thought-id]
  (throw (ex-info (str "Rethought [ID: " thought-id "] not found")
                  {:type    :resource-not-found
                   :subject :rethought
                   :cause   (str "rethought with ID '" thought-id "' not found")
                   :context {:rethought-id thought-id}})))

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
  [thought-id user-id]
  (throw (ex-info (str "User [ID: " user-id "] already likes Thought [ID: " thought-id "]")
                  {:type    :invalid-action
                   :subject :like
                   :cause   "you cannot like the same thought more than once"
                   :context {:thought-id thought-id :user-id user-id}})))

(defn- throw-invalid-unlike!
  [thought-id user-id]
  (throw (ex-info (str "Thought [ID: " thought-id "] has not been liked by User [ID: " user-id "] yet")
                  {:type    :invalid-action
                   :subject :unlike
                   :cause   "you cannot unlike a thought you do not like yet"
                   :context {:thought-id thought-id :user-id user-id}})))

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
  (some #(= followed-id (:id %)) (p.repository/fetch-following! (:repository service) follower-id)))

(defn- build-feed
  "Creates a collection of thoughts (length <= 100) from users in `following` sorted by `:publish-date`."
  [service following]
  (->> following
       (map :id)
       (map (fn [user-id] {:user-id user-id}))
       (map (fn [user-id-criteria] (p.repository/fetch-thoughts! (:repository service) user-id-criteria)))
       (map (fn [user-thoughts] (core/sort-by-date user-thoughts)))
       (core/merge-by-date 100)))