(ns twitter-clj.adapter.http.handler
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [clojure.string :refer [split]]
            [compojure.core :refer :all]
            [schema.core :as s]
            [taoensso.timbre :as log]
            [twitter-clj.application.config :refer [http-api-jws-secret]]
            [twitter-clj.application.port.service :as service]
            [twitter-clj.adapter.http.util :refer :all]
            [twitter-clj.schema.http :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.middleware.reload :refer [wrap-reload]])
  (:import [clojure.lang ExceptionInfo]))

(declare add-links)

(defn signup
  [req service]
  (s/validate SignupRequest (:body req))
  (let [{:keys [name email username password]} (:body req)]
    (let [user (service/create-user service name email username password)
          user-info (str "'" (:name user) "'" " @" (:username user) " [" (:email user) "]")]
      (log/info "Create new user" user-info)
      (created (add-links :user req user)))))

(defn login
  [req service]
  (s/validate LoginRequest (:body req))
  (let [{:keys [user-id password]} (:body req)]
    (if (and (service/user-exists? service user-id)
             (service/password-match? service user-id password))
      (let [session-id (service/login service user-id)
            token (create-token user-id :user session-id)]
        (log/info "Login of user" (f-id user-id))
        (ok-with-success {:token token}))
      (do (log-failure "Login failed (wrong user ID or password)")
          (bad-request {:cause "wrong user ID or password"})))))

(defn logout
  [req service]
  (let [session-id (get-session-id req)
        user-id (get-user-id req)]
    (log/info "Logout of user" (f-id user-id) "from session" (f-id session-id))
    (service/logout service session-id)
    (ok-with-success {:status "logged out"})))

(defn logout-all
  [req service]
  (let [user-id (get-user-id req)]
    (log/info "Logout of user" (f-id user-id) "from all sessions")
    (service/logout-all service user-id)
    (ok-with-success {:status "logged out from all sessions"})))

(defn feed
  [req service]
  (let [user-id (get-user-id req)
        limit (Integer/parseInt (get-parameter req :limit))
        offset (Integer/parseInt (get-parameter req :offset))]
    (log/info "Get feed of user" (f-id user-id))
    (let [tweets (service/get-feed service user-id limit offset)]
      (ok-with-success (map (partial add-links :tweet req) tweets)))))

(defn get-user-by-id
  [req service]
  (let [user-id (get-parameter req :user-id)
        user (service/get-user-by-id service user-id)]
    (log/info "Get user" (f user))
    (ok-with-success (add-links :user req user))))

(defn get-tweets-by-user-id
  [req service]
  (let [user-id (get-parameter req :user-id)
        tweets (service/get-tweets-by-user service user-id)]
    (log/info "Get tweets from user" (f-id user-id))
    (ok-with-success (map (partial add-links :tweet req) tweets))))

(defn get-user-following
  [req service]
  (let [user-id (get-parameter req :user-id)
        following (service/get-following service user-id)]
    (log/info "Get list of following of user" (f-id user-id))
    (ok-with-success (map (partial add-links :user req) following))))

(defn get-user-followers
  [req service]
  (let [user-id (get-parameter req :user-id)
        followers (service/get-followers service user-id)]
    (log/info "Get list of followers of user" (f-id user-id))
    (ok-with-success (map (partial add-links :user req) followers))))

(defn get-tweet-by-id
  [req service]
  (let [tweet-id (get-parameter req :tweet-id)
        tweet (service/get-tweet-by-id service tweet-id)]
    (log/info "Get tweet" (f tweet))
    (ok-with-success (add-links :tweet req tweet))))

(defn get-tweets-with-hashtag
  [req service]
  (let [hashtag (get-parameter req :hashtag)
        tweets (service/get-tweets-with-hashtag service hashtag)]
    (log/info "Get tweets with hashtag" (str "#" hashtag))
    (ok-with-success (map (partial add-links :tweet req) tweets))))

(defn get-replies-by-tweet-id
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        replies (service/get-replies-by-tweet-id service source-tweet-id)]
    (log/info "Get replies of tweet" (f-id source-tweet-id))
    (ok-with-success (map (partial add-links :reply req source-tweet-id) replies))))

(defn get-retweets-by-tweet-id
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        retweets (service/get-retweets-by-tweet-id service source-tweet-id)]
    (log/info "Get retweets of tweet" (f-id source-tweet-id))
    (ok-with-success (map (partial add-links :retweet req source-tweet-id) retweets))))

(defn get-retweet-by-id
  [req service]
  (let [retweet-id (get-parameter req :retweet-id)
        retweet (service/get-retweet-by-id service retweet-id)]
    (log/info "Get retweet" (f-id retweet-id))
    (ok-with-success (add-links :retweet req (:source-tweet-id retweet) retweet))))

(defn follow
  [req service]
  (let [user-id (get-user-id req)
        followed-id (get-parameter req :user-id)]
    (log/info "User" (f-id user-id) "follows User" (f-id followed-id))
    (->> (service/follow service user-id followed-id)
         (add-links :user req)
         (ok-with-success))))

(defn unfollow
  [req service]
  (let [user-id (get-user-id req)
        followed-id (get-parameter req :user-id)]
    (log/info "User" (f-id user-id) "unfollows User" (f-id followed-id))
    (->> (service/unfollow service user-id followed-id)
         (add-links :user req)
         (ok-with-success))))

(defn tweet
  [req service]
  (s/validate CreateTweetRequest (:body req))
  (let [user-id (get-user-id req)
        text (get-from-body req :text)
        tweet (service/tweet service user-id text)]
    (log/info "Create new tweet" (f tweet) "of user" (f-id user-id))
    (created (add-links :tweet req tweet))))

(defn reply
  [req service]
  (s/validate ReplyRequest (:body req))
  (let [user-id (get-user-id req)
        source-tweet-id (get-parameter req :tweet-id)
        text (get-from-body req :text)
        reply (service/reply service user-id text source-tweet-id)]
    (log/info "Reply tweet" (f-id source-tweet-id) "of user" (f-id user-id))
    (created (add-links :reply req source-tweet-id reply))))

(defn retweet
  [req service]
  (let [user-id (get-user-id req)
        source-tweet-id (get-parameter req :tweet-id)
        retweet (service/retweet service user-id source-tweet-id)]
    (log/info "Retweet tweet" (f-id source-tweet-id) "of user" (f-id user-id))
    (created (add-links :retweet req source-tweet-id retweet))))

(defn retweet-with-comment
  [req service]
  (s/validate RetweetWithCommentRequest (:body req))
  (let [user-id (get-user-id req)
        source-tweet-id (get-parameter req :tweet-id)
        comment (get-from-body req :comment)
        retweet (service/retweet-with-comment service user-id comment source-tweet-id)]
    (log/info "Retweet tweet with comment" (f-id source-tweet-id) "of user" (f-id user-id))
    (created (add-links :retweet req source-tweet-id retweet))))

(defn like
  [req service]
  (let [user-id (get-user-id req)
        tweet-id (get-parameter req :tweet-id)]
    (log/info "Like tweet" (f-id tweet-id))
    (->> (service/like service user-id tweet-id)
         (add-links :tweet req)
         (ok-with-success))))

(defn unlike
  [req service]
  (let [user-id (get-user-id req)
        tweet-id (get-parameter req :tweet-id)]
    (log/info "Unlike tweet" (f-id tweet-id))
    (->> (service/unlike service user-id tweet-id)
         (add-links :tweet req)
         (ok-with-success))))

;; Exception-handling functions.

(defn- format-failure-info
  [failure-info]
  (update failure-info :type (fn [type] (clojure.string/replace (name type) #"-" " "))))

(defn- format-schema-error
  [schema-error]
  (let [context (schema-error-context schema-error)]
    {:type    "invalid request"
     :subject "schema"
     :cause   "some fields (see context) have invalid data types or are missing"
     :context context}))

(defn- wrap-authenticated
  [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)
      (do (log-failure "User is not authenticated (missing authorization token or not logged in)")
          (unauthorized)))))

(defn- wrap-service-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [failure-info (ex-data e)]
          (if (and (:type failure-info) (:subject failure-info))
            (do (log-failure (.getMessage e))
                (-> failure-info (format-failure-info) (bad-request)))
            (throw e)))))))

(defn- wrap-schema-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [failure-info (ex-data e)]
          (if (= :schema.core/error (:type failure-info))
            (do (log-failure (.getMessage e))
                (-> failure-info (format-schema-error) (bad-request)))
            (throw e)))))))

(defn- wrap-default-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/warn (str-exception e))
        (internal-server-error)))))

;; Routes.

(def ^:private jws-backend (backends/jws {:secret     http-api-jws-secret
                                          :token-name "Bearer"
                                          :options    {:alg :hs512}}))

;; It is needed for HATEAOS.
(def ^:private routes-map {:signup                   (path-prefix "/signup")
                           :login                    (path-prefix "/login")
                           :logout                   (path-prefix "/logout")
                           :logout-all               (path-prefix "/logout/all")
                           :feed                     (path-prefix "/feed")
                           :get-user-by-id           (path-prefix "/user/:user-id")
                           :get-tweets-by-user-id    (path-prefix "/user/:user-id/tweets")
                           :get-user-following       (path-prefix "/user/:user-id/following")
                           :get-replies-by-tweet-id  (path-prefix "/tweet/:tweet-id/replies")
                           :get-user-followers       (path-prefix "/user/:user-id/followers")
                           :get-tweet-by-id          (path-prefix "/tweet/:tweet-id")
                           :get-tweets-with-hashtag  (path-prefix "/tweet/hashtag/:hashtag")
                           :get-retweets-by-tweet-id (path-prefix "/tweet/:tweet-id/retweets")
                           :get-retweet-by-id        (path-prefix "/retweet/:retweet-id")
                           :follow                   (path-prefix "/user/:user-id/follow")
                           :unfollow                 (path-prefix "/user/:user-id/unfollow")
                           :tweet                    (path-prefix "/tweet")
                           :reply                    (path-prefix "/tweet/:tweet-id/reply")
                           :retweet                  (path-prefix "/tweet/:tweet-id/retweet")
                           :retweet-with-comment     (path-prefix "/tweet/:tweet-id/retweet-comment")
                           :like                     (path-prefix "/tweet/:tweet-id/like")
                           :unlike                   (path-prefix "/tweet/:tweet-id/unlike")})

(defn public-routes
  [service]
  (compojure.core/routes
    (POST (:signup routes-map) req (signup req service))
    (POST (:login routes-map) req (login req service))))

(defn user-routes
  [service]
  (compojure.core/routes
    (POST (:logout routes-map) req (logout req service))
    (POST (:logout-all routes-map) req (logout-all req service))
    (GET (:feed routes-map) req (feed req service))
    (GET (:get-user-by-id routes-map) req (get-user-by-id req service))
    (GET (:get-tweets-by-user-id routes-map) req (get-tweets-by-user-id req service))
    (GET (:get-user-following routes-map) req (get-user-following req service))
    (GET (:get-user-followers routes-map) req (get-user-followers req service))
    (GET (:get-tweet-by-id routes-map) req (get-tweet-by-id req service))
    (GET (:get-tweets-with-hashtag routes-map) req (get-tweets-with-hashtag req service))
    (GET (:get-replies-by-tweet-id routes-map) req (get-replies-by-tweet-id req service))
    (GET (:get-retweets-by-tweet-id routes-map) req (get-retweets-by-tweet-id req service))
    (GET (:get-retweet-by-id routes-map) req (get-retweet-by-id req service))
    (POST (:follow routes-map) req (follow req service))
    (POST (:unfollow routes-map) req (unfollow req service))
    (POST (:tweet routes-map) req (tweet req service))
    (POST (:reply routes-map) req (reply req service))
    (POST (:retweet routes-map) req (retweet req service))
    (POST (:retweet-with-comment routes-map) req (retweet-with-comment req service))
    (POST (:like routes-map) req (like req service))
    (POST (:unlike routes-map) req (unlike req service))))

(defn handler
  [service]
  (-> (compojure.core/routes
        (public-routes service)
        (-> (user-routes service)
            (wrap-authenticated)
            (wrap-authentication jws-backend)))
      (wrap-service-exception)
      (wrap-schema-exception)
      ;(wrap-default-exception)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-defaults api-defaults)
      (wrap-reload)))

;; HATEOAS.

(defn- get-host
  [{:keys [scheme server-name server-port]}]
  (str (name scheme) "://" server-name ":" server-port))

(defn- replace-path
  [path replacements]
  (let [replacements-str (zipmap (map str (keys replacements)) (vals replacements))]
    (->> path
         (#(split % #"/"))
         (replace replacements-str)
         (apply join-path))))

(defn- make-links-map
  [host response links]
  (assoc response :_links
                  (zipmap (keys links)
                          (map (fn [val] (let [path-key (val 0)
                                               path-variables (val 1)]
                                           {:href (join-path host (replace-path (path-key routes-map) path-variables))}))
                               (vals links)))))

(defmulti add-links (fn [selector-key & _args] selector-key))

(defmethod add-links :user
  [_ req tweet]
  (let [{:keys [id]} tweet]
    (make-links-map (get-host req) tweet {:self      [:get-user-by-id {:user-id id}]
                                          :tweets    [:get-tweets-by-user-id {:user-id id}]
                                          :following [:get-user-following {:user-id id}]
                                          :followers [:get-user-followers {:user-id id}]})))

(defmethod add-links :tweet
  [_ req tweet]
  (let [{:keys [id user-id]} tweet]
    (make-links-map (get-host req) tweet {:self     [:get-tweet-by-id {:tweet-id id}]
                                          :user     [:get-user-by-id {:user-id user-id}]
                                          :replies  [:get-replies-by-tweet-id {:tweet-id id}]
                                          :retweets [:get-retweets-by-tweet-id {:tweet-id id}]})))

(defmethod add-links :reply
  [_ req source-tweet-id tweet]
  (let [{:keys [id user-id]} tweet]
    (make-links-map (get-host req) tweet {:self         [:get-tweet-by-id {:tweet-id id}]
                                          :user         [:get-user-by-id {:user-id user-id}]
                                          :replies      [:get-replies-by-tweet-id {:tweet-id id}]
                                          :retweets     [:get-retweets-by-tweet-id {:tweet-id id}]
                                          :source-tweet [:get-tweet-by-id {:tweet-id source-tweet-id}]})))

(defmethod add-links :retweet
  [_ req source-tweet-id retweet]
  (let [{:keys [id user-id]} retweet]
    (make-links-map (get-host req) retweet {:self         [:get-retweet-by-id {:retweet-id id}]
                                            :user         [:get-user-by-id {:user-id user-id}]
                                            :source-tweet [:get-tweet-by-id {:tweet-id source-tweet-id}]})))