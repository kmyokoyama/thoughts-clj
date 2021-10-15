(ns thoughts.adapter.http.handler
  (:require [buddy.auth :as auth]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :as middleware]
            [clojure.string :as string]
            [compojure.core :as compojure]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.json :as json]
            [ring.middleware.reload :as reload]
            [schema.core :as s]
            [taoensso.timbre :as log]
            [thoughts.adapter.http.util :as a.http.util]
            [thoughts.application.config :as config]
            [thoughts.port.service :as p.service]
            [thoughts.schema.http :as s.http])
  (:import [clojure.lang ExceptionInfo]))

(declare add-links)

(defn signup
  [req service]
  (s/validate s.http/SignupRequest (:body req))
  (let [{:keys [name email username password]} (:body req)]
    (let [user (p.service/create-user service name email username password)
          user-info (str "'" (:name user) "'" " @" (:username user) " [" (:email user) "]")
          user-id (:id user)]
      (log/info "Create new user" (a.http.util/f-id user-id) user-info)
      (a.http.util/created (add-links :user req user)))))

(defn login
  [req service]
  (s/validate s.http/LoginRequest (:body req))
  (let [{:keys [user-id password]} (:body req)]
    (if (and (p.service/user-exists? service user-id)
             (p.service/password-match? service user-id password))
      (let [session-id (p.service/login service user-id)
            token (a.http.util/create-token user-id :user session-id)]
        (log/info "Login of user" (a.http.util/f-id user-id))
        (a.http.util/ok-with-success {:token token}))
      (do (a.http.util/log-failure "Login failed (wrong user ID or password)")
          (a.http.util/bad-request {:cause "wrong user ID or password"})))))

(defn logout
  [req service]
  (let [session-id (a.http.util/get-session-id req)
        user-id (a.http.util/get-user-id req)]
    (log/info "Logout of user" (a.http.util/f-id user-id) "from session" (a.http.util/f-id session-id))
    (p.service/logout service session-id)
    (a.http.util/ok-with-success {:status "logged out"})))

(defn logout-all
  [req service]
  (let [user-id (a.http.util/get-user-id req)]
    (log/info "Logout of user" (a.http.util/f-id user-id) "from all sessions")
    (p.service/logout-all service user-id)
    (a.http.util/ok-with-success {:status "logged out from all sessions"})))

(defn feed
  [req service]
  (let [user-id (a.http.util/get-user-id req)
        limit (or (a.http.util/str->int (a.http.util/get-parameter req :limit)) 50)
        offset (or (a.http.util/str->int (a.http.util/get-parameter req :offset)) 0)]
    (log/info "Get feed of user" (a.http.util/f-id user-id))
    (let [thoughts (p.service/get-feed service user-id limit offset)]
      (a.http.util/ok-with-success (map (partial add-links :thought req) thoughts)))))

(defn get-user-by-id
  [req service]
  (let [user-id (a.http.util/get-parameter req :user-id)
        user (p.service/get-user-by-id service user-id)]
    (log/info "Get user" (a.http.util/f user))
    (a.http.util/ok-with-success (add-links :user req user))))

(defn get-thoughts-by-user-id
  [req service]
  (let [user-id (a.http.util/get-parameter req :user-id)
        thoughts (p.service/get-thoughts-by-user service user-id)]
    (log/info "Get thoughts from user" (a.http.util/f-id user-id))
    (a.http.util/ok-with-success (map (partial add-links :thought req) thoughts))))

(defn get-user-following
  [req service]
  (let [user-id (a.http.util/get-parameter req :user-id)
        following (p.service/get-following service user-id)]
    (log/info "Get list of following of user" (a.http.util/f-id user-id))
    (a.http.util/ok-with-success (map (partial add-links :user req) following))))

(defn get-user-followers
  [req service]
  (let [user-id (a.http.util/get-parameter req :user-id)
        followers (p.service/get-followers service user-id)]
    (log/info "Get list of followers of user" (a.http.util/f-id user-id))
    (a.http.util/ok-with-success (map (partial add-links :user req) followers))))

(defn get-thought-by-id
  [req service]
  (let [thought-id (a.http.util/get-parameter req :thought-id)
        thought (p.service/get-thought-by-id service thought-id)]
    (log/info "Get thought" (a.http.util/f thought))
    (a.http.util/ok-with-success (add-links :thought req thought))))

(defn get-thoughts-with-hashtag
  [req service]
  (let [hashtag (a.http.util/get-parameter req :hashtag)
        thoughts (p.service/get-thoughts-with-hashtag service hashtag)]
    (log/info "Get thoughts with hashtag" (str "#" hashtag))
    (a.http.util/ok-with-success (map (partial add-links :thought req) thoughts))))

(defn get-replies-by-thought-id
  [req service]
  (let [source-thought-id (a.http.util/get-parameter req :thought-id)
        replies (p.service/get-replies-by-thought-id service source-thought-id)]
    (log/info "Get replies of thought" (a.http.util/f-id source-thought-id))
    (a.http.util/ok-with-success (map (partial add-links :reply req source-thought-id) replies))))

(defn get-rethoughts-by-thought-id
  [req service]
  (let [source-thought-id (a.http.util/get-parameter req :thought-id)
        rethoughts (p.service/get-rethoughts-by-thought-id service source-thought-id)]
    (log/info "Get rethoughts of thought" (a.http.util/f-id source-thought-id))
    (a.http.util/ok-with-success (map (partial add-links :rethought req source-thought-id) rethoughts))))

(defn get-rethought-by-id
  [req service]
  (let [rethought-id (a.http.util/get-parameter req :rethought-id)
        rethought (p.service/get-rethought-by-id service rethought-id)]
    (log/info "Get rethought" (a.http.util/f-id rethought-id))
    (a.http.util/ok-with-success (add-links :rethought req (:source-thought-id rethought) rethought))))

(defn follow
  [req service]
  (let [user-id (a.http.util/get-user-id req)
        followed-id (a.http.util/get-parameter req :user-id)]
    (log/info "User" (a.http.util/f-id user-id) "follows User" (a.http.util/f-id followed-id))
    (->> (p.service/follow service user-id followed-id)
         (add-links :user req)
         (a.http.util/ok-with-success))))

(defn unfollow
  [req service]
  (let [user-id (a.http.util/get-user-id req)
        followed-id (a.http.util/get-parameter req :user-id)]
    (log/info "User" (a.http.util/f-id user-id) "unfollows User" (a.http.util/f-id followed-id))
    (->> (p.service/unfollow service user-id followed-id)
         (add-links :user req)
         (a.http.util/ok-with-success))))

(defn thought
  [req service]
  (s/validate s.http/CreateThoughtRequest (:body req))
  (let [user-id (a.http.util/get-user-id req)
        text (a.http.util/get-from-body req :text)
        thought (p.service/thought service user-id text)]
    (log/info "Create new thought" (a.http.util/f thought) "of user" (a.http.util/f-id user-id))
    (a.http.util/created (add-links :thought req thought))))

(defn reply
  [req service]
  (s/validate s.http/ReplyRequest (:body req))
  (let [user-id (a.http.util/get-user-id req)
        source-thought-id (a.http.util/get-parameter req :thought-id)
        text (a.http.util/get-from-body req :text)
        reply (p.service/reply service user-id text source-thought-id)]
    (log/info "Reply thought" (a.http.util/f-id source-thought-id) "of user" (a.http.util/f-id user-id))
    (a.http.util/created (add-links :reply req source-thought-id reply))))

(defn rethought
  [req service]
  (let [user-id (a.http.util/get-user-id req)
        source-thought-id (a.http.util/get-parameter req :thought-id)
        rethought (p.service/rethought service user-id source-thought-id)]
    (log/info "Rethought thought" (a.http.util/f-id source-thought-id) "of user" (a.http.util/f-id user-id))
    (a.http.util/created (add-links :rethought req source-thought-id rethought))))

(defn rethought-with-comment
  [req service]
  (s/validate s.http/RethoughtWithCommentRequest (:body req))
  (let [user-id (a.http.util/get-user-id req)
        source-thought-id (a.http.util/get-parameter req :thought-id)
        comment (a.http.util/get-from-body req :comment)
        rethought (p.service/rethought-with-comment service user-id comment source-thought-id)]
    (log/info "Rethought thought with comment" (a.http.util/f-id source-thought-id) "of user" (a.http.util/f-id user-id))
    (a.http.util/created (add-links :rethought req source-thought-id rethought))))

(defn like
  [req service]
  (let [user-id (a.http.util/get-user-id req)
        thought-id (a.http.util/get-parameter req :thought-id)]
    (log/info "Like thought" (a.http.util/f-id thought-id))
    (->> (p.service/like service user-id thought-id)
         (add-links :thought req)
         (a.http.util/ok-with-success))))

(defn unlike
  [req service]
  (let [user-id (a.http.util/get-user-id req)
        thought-id (a.http.util/get-parameter req :thought-id)]
    (log/info "Unlike thought" (a.http.util/f-id thought-id))
    (->> (p.service/unlike service user-id thought-id)
         (add-links :thought req)
         (a.http.util/ok-with-success))))

;; Exception-handling functions.

(defn- format-failure-info
  [failure-info]
  (update failure-info :type (fn [type] (clojure.string/replace (name type) #"-" " "))))

(defn- format-schema-error
  [schema-error]
  (let [context (a.http.util/schema-error-context schema-error)]
    {:type    "invalid request"
     :subject "schema"
     :cause   "some fields (see context) have invalid data types or are missing"
     :context context}))

(defn- wrap-authenticated
  [handler]
  (fn [request]
    (if (auth/authenticated? request)
      (handler request)
      (do (a.http.util/log-failure "User is not authenticated (missing authorization token or not logged in)")
          (a.http.util/unauthorized)))))

(defn- wrap-service-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [failure-info (ex-data e)]
          (if (and (:type failure-info) (:subject failure-info))
            (do (a.http.util/log-failure (.getMessage e))
                (-> failure-info (format-failure-info) (a.http.util/bad-request)))
            (throw e)))))))

(defn- wrap-schema-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [failure-info (ex-data e)]
          (if (= :schema.core/error (:type failure-info))
            (do (a.http.util/log-failure (.getMessage e))
                (-> failure-info (format-schema-error) (a.http.util/bad-request)))
            (throw e)))))))

(defn- wrap-default-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/warn (a.http.util/str-exception e))
        (a.http.util/internal-server-error)))))

;; Routes.

(def ^:private jws-backend (backends/jws {:secret     config/http-api-jws-secret
                                          :token-name "Bearer"
                                          :options    {:alg :hs512}}))

;; It is needed for HATEAOS.
(def ^:private routes-map {:signup                       (a.http.util/path-prefix "/signup")
                           :login                        (a.http.util/path-prefix "/login")
                           :logout                       (a.http.util/path-prefix "/logout")
                           :logout-all                   (a.http.util/path-prefix "/logout/all")
                           :feed                         (a.http.util/path-prefix "/feed")
                           :get-user-by-id               (a.http.util/path-prefix "/user/:user-id")
                           :get-thoughts-by-user-id      (a.http.util/path-prefix "/user/:user-id/thoughts")
                           :get-user-following           (a.http.util/path-prefix "/user/:user-id/following")
                           :get-replies-by-thought-id    (a.http.util/path-prefix "/thought/:thought-id/replies")
                           :get-user-followers           (a.http.util/path-prefix "/user/:user-id/followers")
                           :get-thought-by-id            (a.http.util/path-prefix "/thought/:thought-id")
                           :get-thoughts-with-hashtag    (a.http.util/path-prefix "/thought/hashtag/:hashtag")
                           :get-rethoughts-by-thought-id (a.http.util/path-prefix "/thought/:thought-id/rethoughts")
                           :get-rethought-by-id          (a.http.util/path-prefix "/rethought/:rethought-id")
                           :follow                       (a.http.util/path-prefix "/user/:user-id/follow")
                           :unfollow                     (a.http.util/path-prefix "/user/:user-id/unfollow")
                           :thought                      (a.http.util/path-prefix "/thought")
                           :reply                        (a.http.util/path-prefix "/thought/:thought-id/reply")
                           :rethought                    (a.http.util/path-prefix "/thought/:thought-id/rethought")
                           :rethought-with-comment       (a.http.util/path-prefix "/thought/:thought-id/rethought-comment")
                           :like                         (a.http.util/path-prefix "/thought/:thought-id/like")
                           :unlike                       (a.http.util/path-prefix "/thought/:thought-id/unlike")})

(defn public-routes
  [service]
  (compojure.core/routes
   (compojure/POST (:signup routes-map) req (signup req service))
   (compojure/POST (:login routes-map) req (login req service))))

(defn user-routes
  [service]
  (compojure.core/routes
   (compojure/POST (:logout routes-map) req (logout req service))
   (compojure/POST (:logout-all routes-map) req (logout-all req service))
   (compojure/GET (:feed routes-map) req (feed req service))
   (compojure/GET (:get-user-by-id routes-map) req (get-user-by-id req service))
   (compojure/GET (:get-thoughts-by-user-id routes-map) req (get-thoughts-by-user-id req service))
   (compojure/GET (:get-user-following routes-map) req (get-user-following req service))
   (compojure/GET (:get-user-followers routes-map) req (get-user-followers req service))
   (compojure/GET (:get-thought-by-id routes-map) req (get-thought-by-id req service))
   (compojure/GET (:get-thoughts-with-hashtag routes-map) req (get-thoughts-with-hashtag req service))
   (compojure/GET (:get-replies-by-thought-id routes-map) req (get-replies-by-thought-id req service))
   (compojure/GET (:get-rethoughts-by-thought-id routes-map) req (get-rethoughts-by-thought-id req service))
   (compojure/GET (:get-rethought-by-id routes-map) req (get-rethought-by-id req service))
   (compojure/POST (:follow routes-map) req (follow req service))
   (compojure/POST (:unfollow routes-map) req (unfollow req service))
   (compojure/POST (:thought routes-map) req (thought req service))
   (compojure/POST (:reply routes-map) req (reply req service))
   (compojure/POST (:rethought routes-map) req (rethought req service))
   (compojure/POST (:rethought-with-comment routes-map) req (rethought-with-comment req service))
   (compojure/POST (:like routes-map) req (like req service))
   (compojure/POST (:unlike routes-map) req (unlike req service))))

(defn handler
  [service]
  (-> (compojure.core/routes
       (public-routes service)
       (-> (user-routes service)
           (wrap-authenticated)
           (middleware/wrap-authentication jws-backend)))
      (wrap-service-exception)
      (wrap-schema-exception)
      ;(wrap-default-exception)
      (json/wrap-json-body {:keywords? true :bigdecimals? true})
      (defaults/wrap-defaults defaults/api-defaults)
      (reload/wrap-reload)))

;; HATEOAS.

(defn- get-host
  [{:keys [scheme server-name server-port]}]
  (str (name scheme) "://" server-name ":" server-port))

(defn- replace-path
  [path replacements]
  (let [replacements-str (zipmap (map str (keys replacements)) (vals replacements))]
    (->> path
         (#(string/split % #"/"))
         (replace replacements-str)
         (apply a.http.util/join-path))))

(defn- make-links-map
  [host response links]
  (assoc response :_links
         (zipmap (keys links)
                 (map (fn [val] (let [path-key (val 0)
                                      path-variables (val 1)]
                                  {:href (a.http.util/join-path host (replace-path (path-key routes-map) path-variables))}))
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