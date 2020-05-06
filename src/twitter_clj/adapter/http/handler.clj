(ns twitter-clj.adapter.http.handler
  (:require [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [clojure.string :refer [split]]
            [compojure.core :refer :all]
            [taoensso.timbre :as log]
            [twitter-clj.application.config :refer [http-api-jws-secret]]
            [twitter-clj.application.service :as service]
            [twitter-clj.adapter.http.util :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]])
  (:import [clojure.lang ExceptionInfo]))

(declare add-links)

(defn login
  [req service]
  (let [user-id (get-from-body req :user-id)
        password (get-from-body req :password)]
    (if (and (service/user-exists? service user-id)
             (service/password-match? service user-id password))
      (if-not (service/logged-in? service user-id)
        (let [token (create-token user-id :user)]
          (log/info "Login of user" (f-id user-id))
          (service/login service user-id)
          (ok-with-success {:token token}))
        (do (log-failure "Login failed (user" (f-id user-id) "already logged in)")
            (bad-request {:cause "you are already logged in"})))
      (do (log-failure "Login failed (wrong user ID or password)")
          (bad-request {:cause "wrong user ID or password"})))))

(defn logout
  [req service]
  (let [user-id (get-user-id req)]
    (log/info "Logout user" (f-id user-id))
    (service/logout service user-id)
    (ok-with-success {:status "logged out"})))

(defn signup
  [req service]
  (let [{:keys [name email username password]} (:body req)]
    (let [user (service/add-user service name email username password)
          user-info (str "'" (:name user) "'" " @" (:username user) " [" (:email user) "]")]
      (log/info "Add user" user-info)
      (created (add-links :user req user)))))

(defn get-user-by-id
  [req service]
  (let [user-id (get-parameter req :user-id)
        user (service/get-user-by-id service user-id)]
    (log/info "Get user" (f user))
    (ok-with-success (add-links :user req user))))

(defn add-tweet
  [req service]
  (let [user-id (get-user-id req)
        text (get-from-body req :text)
        tweet (service/add-tweet service user-id text)]
    (log/info "Add tweet" (f tweet) "from user" (f-id user-id))
    (created (add-links :tweet req tweet))))

(defn get-tweet-by-id
  [req service]
  (let [tweet-id (get-parameter req :tweet-id)
        tweet (service/get-tweet-by-id service tweet-id)]
    (log/info "Get tweet" (f tweet))
    (ok-with-success (add-links :tweet req tweet))))

(defn get-tweets-by-user
  [req service]
  (let [user-id (get-parameter req :user-id)
        tweets (service/get-tweets-by-user service user-id)]
    (log/info "Get tweets from user" (f-id user-id))
    (ok-with-success (map (partial add-links :tweet req) tweets))))

(defn add-reply
  [req service]
  (let [user-id (get-user-id req)
        source-tweet-id (get-parameter req :tweet-id)
        text (get-from-body req :text)
        reply (service/add-reply service user-id text source-tweet-id)]
    (log/info "Reply tweet" (f-id source-tweet-id) "from user" (f-id user-id))
    (created (add-links :reply req source-tweet-id reply))))

(defn add-retweet
  [req service]
  (let [user-id (get-user-id req)
        source-tweet-id (get-parameter req :tweet-id)
        retweet (service/retweet service user-id source-tweet-id)]
    (log/info "Retweet tweet" (f-id source-tweet-id) "from user" (f-id user-id))
    (created (add-links :retweet req source-tweet-id retweet))))

(defn add-retweet-with-comment
  [req service]
  (let [user-id (get-user-id req)
        source-tweet-id (get-parameter req :tweet-id)
        comment (get-from-body req :comment)
        retweet (service/retweet-with-comment service user-id comment source-tweet-id)]
    (log/info "Retweet tweet" (f-id source-tweet-id) "from user" (f-id user-id))
    (created (add-links :retweet req source-tweet-id retweet))))

(defn get-retweet-by-id
  [req service]
  (let [retweet-id (get-parameter req :retweet-id)
        retweet (service/get-retweet-by-id service retweet-id)]
    (log/info "Get retweet" (f-id retweet-id))
    (ok-with-success (add-links :retweet req (:source-tweet-id retweet) retweet))))

(defn get-retweets-by-tweet-id
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        retweets (service/get-retweets-by-tweet-id service source-tweet-id)]
    (log/info "Get retweets of tweet" (f-id source-tweet-id))
    (ok-with-success (map (partial add-links :retweet req source-tweet-id) retweets))))

(defn get-replies-by-tweet-id
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        replies (service/get-replies-by-tweet-id service source-tweet-id)]
    (log/info "Get replies of tweet" (f-id source-tweet-id))
    (ok-with-success (map (partial add-links :reply req source-tweet-id) replies))))

(defn- like-tweet
  [req service user-id tweet-id]
  (log/info "Like tweet" (f-id tweet-id))
  (try
    (->> (service/like service user-id tweet-id)
         (add-links :tweet req)
         (ok-with-success))))

(defn- unlike-tweet
  [req service user-id tweet-id]
  (log/info "Unlike tweet" (f-id tweet-id))
  (->> (service/unlike service user-id tweet-id)
       (add-links :tweet req)
       (ok-with-success)))

(defn tweet-react
  [req service]
  (let [user-id (get-user-id req)
        tweet-id (get-parameter req :tweet-id)
        reaction (keyword (get-from-body req :reaction))]
    ;; TODO: Maybe we could refactor it.
    (cond
      (nil? tweet-id) (ok-with-failure {:cause     "missing parameter"
                                        :parameter "tweet-id"})
      (nil? user-id) (ok-with-failure {:cause     "missing parameter"
                                       :parameter "user-id"})
      :default (case reaction
                 :like (like-tweet req service user-id tweet-id)
                 :unlike (unlike-tweet req service user-id tweet-id)
                 (ok-with-failure {:cause     "missing parameter"
                                   :parameter "reaction"})))))

;; Exception-handling functions.

(defn- format-failure-info
  [failure-info]
  (update failure-info :type (fn [type] (clojure.string/replace (name type) #"-" " "))))

(defn- wrap-authenticated
  [handler service]
  (fn [request]
    (if (and (authenticated? request)
             (service/logged-in? service (get-in request [:identity :user-id])))
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

(defn- wrap-default-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/debug (.getMessage e))
        (.printStackTrace e)
        (ok-with-failure {:type "unknown error" :cause (.getMessage e)})))))

;; Routes.

(def ^:private jws-backend (backends/jws {:secret http-api-jws-secret
                                          :token-name "Bearer"
                                          :options {:alg :hs512}}))

(def ^:private routes-map {:get-tweet-by-id (path-prefix "/tweet/:tweet-id")
                           :get-tweets-by-user-id (path-prefix "/user/:user-id/tweets")
                           :get-user-by-id (path-prefix "/user/:user-id")
                           :get-replies-by-tweet-id (path-prefix "/tweet/:tweet-id/replies")
                           :get-retweets-by-tweet-id (path-prefix "/tweet/:tweet-id/retweets")
                           :get-retweet-by-id (path-prefix "/retweet/:retweet-id")
                           :add-tweet (path-prefix "/tweet")
                           :add-reply (path-prefix "/tweet/:tweet-id/reply")
                           :add-retweet (path-prefix "/tweet/:tweet-id/retweet")
                           :add-retweet-with-comment (path-prefix "/tweet/:tweet-id/retweet-comment")
                           :tweet-react (path-prefix "/tweet/:tweet-id/react")})

(defn public-routes
  [service]
  (compojure.core/routes
    (POST (path-prefix "/login") req (login req service))
    (POST (path-prefix "/signup") req (signup req service))))

(defn user-routes
  [service]
  (compojure.core/routes
    (POST (path-prefix "/logout") req (logout req service))
    (GET (path-prefix "/user/:user-id") req (get-user-by-id req service))
    (GET (path-prefix "/user/:user-id/tweets") req (get-tweets-by-user req service))
    (GET (path-prefix "/tweet/:tweet-id") req (get-tweet-by-id req service))
    (GET (path-prefix "/tweet/:tweet-id/replies") req (get-replies-by-tweet-id req service))
    (GET (path-prefix "/tweet/:tweet-id/retweets") req (get-retweets-by-tweet-id req service))
    (GET (path-prefix "/retweet/:retweet-id") req (get-retweet-by-id req service))
    (POST (path-prefix "/tweet") req (add-tweet req service))
    (POST (path-prefix "/tweet/:tweet-id/reply") req (add-reply req service))
    (POST (path-prefix "/tweet/:tweet-id/retweet") req (add-retweet req service))
    (POST (path-prefix "/tweet/:tweet-id/retweet-comment") req (add-retweet-with-comment req service))
    (POST (path-prefix "/tweet/:tweet-id/react") req (tweet-react req service))))

(defn handler
  [service]
  (-> (compojure.core/routes
        (public-routes service)
        (-> (user-routes service)
            (wrap-authenticated service)
            (wrap-authentication jws-backend)))
      (wrap-service-exception)
      (wrap-default-exception)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-defaults api-defaults)))

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
    (make-links-map (get-host req) tweet {:self   [:get-user-by-id {:user-id id}]
                                          :tweets [:get-tweets-by-user-id {:user-id id}]})))

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