(ns twitter-clj.adapter.rest.handler
  (:require [taoensso.timbre :as log]
            [twitter-clj.application.service :as service]
            [twitter-clj.adapter.rest.hateoas :as hateoas]
            [twitter-clj.adapter.rest.util :refer [get-parameter get-from-body
                                                   ok-with-success
                                                   ok-with-failure
                                                   created]]))

(defn add-user
  "This will be moved to user management API in the future."
  [req service]
  (let [{:keys [name email username]} (:body req)]
    (let [user (service/add-user service name email username)
          user-info (str "'" (:name user) "'" " @" (:username user) " [" (:email user) "]")]
      (log/info "Add user" user-info)
      (created user))))

(defn get-user-by-id
  [req service]
  (let [user-id (get-parameter req :user-id)
        user (service/get-user-by-id service user-id)]
    (log/info "Get user with ID" user-id)
    (ok-with-success user)))

(defn add-tweet
  [req service]
  (let [{:keys [user-id text]} (:body req)
        tweet (service/add-tweet service user-id text)]
    (log/info "Add tweet with ID" (:id tweet) "from user with ID" user-id)
    (created (hateoas/add-links :tweet req tweet))))

(defn get-tweet-by-id
  [req service]
  (let [tweet-id (get-parameter req :tweet-id)
        tweet (service/get-tweet-by-id service tweet-id)]
    (log/info "Get tweet with ID" tweet-id)
    (ok-with-success (hateoas/add-links :tweet req tweet))))

(defn get-tweets-by-user
  [req service]
  (let [user-id (get-parameter req :user-id)
        tweets (service/get-tweets-by-user service user-id)]
    (log/info "Get tweets from user with ID" user-id)
    (ok-with-success (map (partial hateoas/add-links :tweet req) tweets))))

(defn add-reply
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id text]} (:body req)
        reply (service/add-reply service user-id text source-tweet-id)]
    (log/info "Reply tweet with ID" source-tweet-id "from user with ID" user-id)
    (created (hateoas/add-links :reply req source-tweet-id reply))))

(defn add-retweet
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        user-id (get-in req [:body :user-id])
        retweet (service/retweet service user-id source-tweet-id)]
    (log/info "Retweet tweet" source-tweet-id "from user with ID" user-id)
    (created (hateoas/add-links :retweet req source-tweet-id retweet))))

(defn add-retweet-with-comment
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id comment]} (:body req)
        retweet (service/retweet-with-comment service user-id comment source-tweet-id)]
    (log/info "Retweet tweet" source-tweet-id "from user with ID" user-id)
    (created (hateoas/add-links :retweet req source-tweet-id retweet))))

(defn get-retweet-by-id
  [req service]
  (let [retweet-id (get-parameter req :retweet-id)
        retweet (service/get-retweet-by-id service retweet-id)]
    (log/info "Get retweet with ID" retweet-id)
    (ok-with-success (hateoas/add-links :retweet req  (:source-tweet-id retweet) retweet))))

(defn get-retweets-by-tweet-id
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        retweets (service/get-retweets-by-tweet-id service source-tweet-id)]
    (log/info "Get retweets of tweet with ID" source-tweet-id)
    (ok-with-success (map (partial hateoas/add-links :retweet req source-tweet-id) retweets))))

(defn get-replies-by-tweet-id
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        replies (service/get-replies-by-tweet-id service source-tweet-id)]
    (log/info "Get replies of tweet with ID" source-tweet-id)
    (ok-with-success (map (partial hateoas/add-links :reply req source-tweet-id) replies))))

(defn- like-tweet
  [req service user-id tweet-id]
  (log/info "Like tweet with ID" tweet-id)
  (try
    (->> (service/like service user-id tweet-id)
        (hateoas/add-links :tweet req)
        (ok-with-success))))

(defn- unlike-tweet
  [req service user-id tweet-id]
  (log/info "Unlike tweet with ID" tweet-id)
  (->> (service/unlike service user-id tweet-id)
       (hateoas/add-links :tweet req)
       (ok-with-success)))

(defn tweet-react
  [req service]
  (let [tweet-id (get-parameter req :tweet-id)
        user-id (get-from-body req :user-id)
        reaction (keyword (get-from-body req :reaction))]
    ;; TODO: Maybe we could refactor it.
    (cond
      (nil? tweet-id) (ok-with-failure {:cause "missing parameter"
                                        :parameter "tweet-id"})
      (nil? user-id) (ok-with-failure {:cause "missing parameter"
                                       :parameter "user-id"})
      :default (case reaction
                 :like (like-tweet req service user-id tweet-id)
                 :unlike (unlike-tweet req service user-id tweet-id)
                 (ok-with-failure {:cause "missing parameter"
                                   :parameter "reaction"})))))

;; Exception-handling functions.

(defn wrap-resource-not-found
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (case (:type (ex-data e))
          :resource-not-found (let [{:keys [resource-type resource-id]} (ex-data e)]
                                (log/warn "[Failure]" (.getMessage e))
                                (ok-with-failure {:cause "resource not found"
                                                  :resource-type resource-type
                                                  :resource-id resource-id}))
          (throw e))))))

(defn wrap-duplicate-resource
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (case (:type (ex-data e))
          :duplicate-resource (let [{:keys [resource-type resource-attribute resource-attribute-value]} (ex-data e)]
                                (log/warn "[Failure]" (.getMessage e))
                                (ok-with-failure {:cause "resource already exists"
                                                  :resource-type resource-type
                                                  :resource-attribute resource-attribute
                                                  :resource-attribute-value resource-attribute-value}))
          (throw e))))))

(defn wrap-default-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/debug e)
        (ok-with-failure {:cause "unknown error" :message (.getMessage e)})))))