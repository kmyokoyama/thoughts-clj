(ns thoughts.adapter.http.handler
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [clojure.string :refer [split]]
            [compojure.core :refer :all]
            [schema.core :as s]
            [taoensso.timbre :as log]
            [thoughts.application.config :refer [http-api-jws-secret]]
            [thoughts.application.port.service :as service]
            [thoughts.adapter.http.util :refer :all]
            [thoughts.schema.http :refer :all]
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
          user-info (str "'" (:name user) "'" " @" (:username user) " [" (:email user) "]")
          user-id (:id user)]
      (log/info "Create new user" (f-id user-id) user-info)
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
        limit (or (str->int (get-parameter req :limit)) 50)
        offset (or (str->int (get-parameter req :offset)) 0)]
    (log/info "Get feed of user" (f-id user-id))
    (let [thoughts (service/get-feed service user-id limit offset)]
      (ok-with-success (map (partial add-links :thought req) thoughts)))))

(defn get-user-by-id
  [req service]
  (let [user-id (get-parameter req :user-id)
        user (service/get-user-by-id service user-id)]
    (log/info "Get user" (f user))
    (ok-with-success (add-links :user req user))))

(defn get-thoughts-by-user-id
  [req service]
  (let [user-id (get-parameter req :user-id)
        thoughts (service/get-thoughts-by-user service user-id)]
    (log/info "Get thoughts from user" (f-id user-id))
    (ok-with-success (map (partial add-links :thought req) thoughts))))

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

(defn get-thought-by-id
  [req service]
  (let [thought-id (get-parameter req :thought-id)
        thought (service/get-thought-by-id service thought-id)]
    (log/info "Get thought" (f thought))
    (ok-with-success (add-links :thought req thought))))

(defn get-thoughts-with-hashtag
  [req service]
  (let [hashtag (get-parameter req :hashtag)
        thoughts (service/get-thoughts-with-hashtag service hashtag)]
    (log/info "Get thoughts with hashtag" (str "#" hashtag))
    (ok-with-success (map (partial add-links :thought req) thoughts))))

(defn get-replies-by-thought-id
  [req service]
  (let [source-thought-id (get-parameter req :thought-id)
        replies (service/get-replies-by-thought-id service source-thought-id)]
    (log/info "Get replies of thought" (f-id source-thought-id))
    (ok-with-success (map (partial add-links :reply req source-thought-id) replies))))

(defn get-rethoughts-by-thought-id
  [req service]
  (let [source-thought-id (get-parameter req :thought-id)
        rethoughts (service/get-rethoughts-by-thought-id service source-thought-id)]
    (log/info "Get rethoughts of thought" (f-id source-thought-id))
    (ok-with-success (map (partial add-links :rethought req source-thought-id) rethoughts))))

(defn get-rethought-by-id
  [req service]
  (let [rethought-id (get-parameter req :rethought-id)
        rethought (service/get-rethought-by-id service rethought-id)]
    (log/info "Get rethought" (f-id rethought-id))
    (ok-with-success (add-links :rethought req (:source-thought-id rethought) rethought))))

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

(defn thought
  [req service]
  (s/validate CreateThoughtRequest (:body req))
  (let [user-id (get-user-id req)
        text (get-from-body req :text)
        thought (service/thought service user-id text)]
    (log/info "Create new thought" (f thought) "of user" (f-id user-id))
    (created (add-links :thought req thought))))

(defn reply
  [req service]
  (s/validate ReplyRequest (:body req))
  (let [user-id (get-user-id req)
        source-thought-id (get-parameter req :thought-id)
        text (get-from-body req :text)
        reply (service/reply service user-id text source-thought-id)]
    (log/info "Reply thought" (f-id source-thought-id) "of user" (f-id user-id))
    (created (add-links :reply req source-thought-id reply))))

(defn rethought
  [req service]
  (let [user-id (get-user-id req)
        source-thought-id (get-parameter req :thought-id)
        rethought (service/rethought service user-id source-thought-id)]
    (log/info "Rethought thought" (f-id source-thought-id) "of user" (f-id user-id))
    (created (add-links :rethought req source-thought-id rethought))))

(defn rethought-with-comment
  [req service]
  (s/validate RethoughtWithCommentRequest (:body req))
  (let [user-id (get-user-id req)
        source-thought-id (get-parameter req :thought-id)
        comment (get-from-body req :comment)
        rethought (service/rethought-with-comment service user-id comment source-thought-id)]
    (log/info "Rethought thought with comment" (f-id source-thought-id) "of user" (f-id user-id))
    (created (add-links :rethought req source-thought-id rethought))))

(defn like
  [req service]
  (let [user-id (get-user-id req)
        thought-id (get-parameter req :thought-id)]
    (log/info "Like thought" (f-id thought-id))
    (->> (service/like service user-id thought-id)
         (add-links :thought req)
         (ok-with-success))))

(defn unlike
  [req service]
  (let [user-id (get-user-id req)
        thought-id (get-parameter req :thought-id)]
    (log/info "Unlike thought" (f-id thought-id))
    (->> (service/unlike service user-id thought-id)
         (add-links :thought req)
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
(def ^:private routes-map {:signup                       (path-prefix "/signup")
                           :login                        (path-prefix "/login")
                           :logout                       (path-prefix "/logout")
                           :logout-all                   (path-prefix "/logout/all")
                           :feed                         (path-prefix "/feed")
                           :get-user-by-id               (path-prefix "/user/:user-id")
                           :get-thoughts-by-user-id      (path-prefix "/user/:user-id/thoughts")
                           :get-user-following           (path-prefix "/user/:user-id/following")
                           :get-replies-by-thought-id    (path-prefix "/thought/:thought-id/replies")
                           :get-user-followers           (path-prefix "/user/:user-id/followers")
                           :get-thought-by-id            (path-prefix "/thought/:thought-id")
                           :get-thoughts-with-hashtag    (path-prefix "/thought/hashtag/:hashtag")
                           :get-rethoughts-by-thought-id (path-prefix "/thought/:thought-id/rethoughts")
                           :get-rethought-by-id          (path-prefix "/rethought/:rethought-id")
                           :follow                       (path-prefix "/user/:user-id/follow")
                           :unfollow                     (path-prefix "/user/:user-id/unfollow")
                           :thought                      (path-prefix "/thought")
                           :reply                        (path-prefix "/thought/:thought-id/reply")
                           :rethought                    (path-prefix "/thought/:thought-id/rethought")
                           :rethought-with-comment       (path-prefix "/thought/:thought-id/rethought-comment")
                           :like                         (path-prefix "/thought/:thought-id/like")
                           :unlike                       (path-prefix "/thought/:thought-id/unlike")})

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
    (GET (:get-thoughts-by-user-id routes-map) req (get-thoughts-by-user-id req service))
    (GET (:get-user-following routes-map) req (get-user-following req service))
    (GET (:get-user-followers routes-map) req (get-user-followers req service))
    (GET (:get-thought-by-id routes-map) req (get-thought-by-id req service))
    (GET (:get-thoughts-with-hashtag routes-map) req (get-thoughts-with-hashtag req service))
    (GET (:get-replies-by-thought-id routes-map) req (get-replies-by-thought-id req service))
    (GET (:get-rethoughts-by-thought-id routes-map) req (get-rethoughts-by-thought-id req service))
    (GET (:get-rethought-by-id routes-map) req (get-rethought-by-id req service))
    (POST (:follow routes-map) req (follow req service))
    (POST (:unfollow routes-map) req (unfollow req service))
    (POST (:thought routes-map) req (thought req service))
    (POST (:reply routes-map) req (reply req service))
    (POST (:rethought routes-map) req (rethought req service))
    (POST (:rethought-with-comment routes-map) req (rethought-with-comment req service))
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
  [_ req thought]
  (let [{:keys [id]} thought]
    (make-links-map (get-host req) thought {:self      [:get-user-by-id {:user-id id}]
                                            :thoughts  [:get-thoughts-by-user-id {:user-id id}]
                                            :following [:get-user-following {:user-id id}]
                                            :followers [:get-user-followers {:user-id id}]})))

(defmethod add-links :thought
  [_ req thought]
  (let [{:keys [id user-id]} thought]
    (make-links-map (get-host req) thought {:self       [:get-thought-by-id {:thought-id id}]
                                            :user       [:get-user-by-id {:user-id user-id}]
                                            :replies    [:get-replies-by-thought-id {:thought-id id}]
                                            :rethoughts [:get-rethoughts-by-thought-id {:thought-id id}]})))

(defmethod add-links :reply
  [_ req source-thought-id thought]
  (let [{:keys [id user-id]} thought]
    (make-links-map (get-host req) thought {:self           [:get-thought-by-id {:thought-id id}]
                                            :user           [:get-user-by-id {:user-id user-id}]
                                            :replies        [:get-replies-by-thought-id {:thought-id id}]
                                            :rethoughts     [:get-rethoughts-by-thought-id {:thought-id id}]
                                            :source-thought [:get-thought-by-id {:thought-id source-thought-id}]})))

(defmethod add-links :rethought
  [_ req source-thought-id rethought]
  (let [{:keys [id user-id]} rethought]
    (make-links-map (get-host req) rethought {:self           [:get-rethought-by-id {:rethought-id id}]
                                              :user           [:get-user-by-id {:user-id user-id}]
                                              :source-thought [:get-thought-by-id {:thought-id source-thought-id}]})))