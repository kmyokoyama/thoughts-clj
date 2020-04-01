(ns twitter-clj.adapter.rest.handler
  (:require [taoensso.timbre :as log]
            [twitter-clj.application.service :as service]
            [twitter-clj.adapter.rest.util :refer [get-parameter get-from-body
                                                   ok-with-success
                                                   ok-with-failure
                                                   created]]))

(defn add-user
  "This will be moved to user management API in the future."
  [req service]
  (let [{:keys [name email username]} (:body req)
        user-info (str name " @" username " [" email"]")]
    (log/info "Received request to add user" user-info)
    (let [user (service/add-user service name email username)]
      (created user))))

(defn get-user-by-id
  [req service]
  (let [user-id (get-parameter req :user-id)
        user (service/get-user-by-id service user-id)]
    (log/info "Received request to get user with id" user-id)
    (ok-with-success user)))

(defn add-tweet
  [req service]
  (let [{:keys [user-id text]} (:body req)
        tweet (service/add-tweet service user-id text)]
    (log/info "Received request to add new tweet from user" user-id)
    (created tweet)))

(defn get-tweet-by-id
  [req service]
  (let [tweet-id (get-parameter req :tweet-id)
        tweet (service/get-tweet-by-id service tweet-id)]
    (log/info "Received request to get tweet with id" tweet-id)
    (ok-with-success tweet)))

(defn get-tweets-by-user
  [req service]
  (let [user-id (get-parameter req :user-id)
        tweets (service/get-tweets-by-user service user-id)]
    (log/info "Received request to get tweets from user" user-id)
    (ok-with-success tweets)))

(defn add-reply
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id text]} (:body req)
        reply (service/add-reply service user-id text source-tweet-id)]
    (log/info "Received request to add new reply from user" user-id "to tweet" source-tweet-id)
    (created reply)))

(defn add-retweet
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id text]} (:body req)
        retweet (service/retweet service user-id source-tweet-id)]
    (log/info "Received request to add new retweet from user" user-id "to tweet" source-tweet-id)
    (created retweet)))

(defn add-retweet-with-comment
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id comment]} (:body req)
        retweet (service/retweet-with-comment service user-id comment source-tweet-id)]
    (log/info "Received request to add new retweet from user" user-id "to tweet" source-tweet-id)
    (created retweet)))

(defn get-retweet-by-id
  [req service]
  (let [retweet-id (get-parameter req :retweet-id)
        retweet (service/get-retweet-by-id service retweet-id)]
    (log/info "Received request to get retweet with id" retweet-id)
    (ok-with-success retweet)))

(defn get-retweets-by-tweet-id
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        retweets (service/get-retweets-by-tweet-id service source-tweet-id)]
    (log/info "Received request to get retweets with source tweet id" source-tweet-id)
    (ok-with-success retweets)))

(defn- like-tweet
  [service user-id tweet-id]
  (log/info "Received request to like tweet" tweet-id)
  (try
    (-> (service/like service user-id tweet-id)
        (ok-with-success))))

(defn- unlike-tweet
  [service user-id tweet-id]
  (log/info "Received request to unlike tweet" tweet-id)
  (-> (service/unlike service user-id tweet-id)
      (ok-with-success)))

(defn tweet-react
  [req service]
  (let [tweet-id (get-parameter req :tweet-id)
        user-id (get-from-body req :user-id)
        reaction (keyword (get-from-body req :reaction))]
    (println reaction)
    (case reaction
      :like (like-tweet service user-id tweet-id)
      :unlike (unlike-tweet service user-id tweet-id))))

;; Exception-handling functions.

(defn wrap-resource-not-found
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (case (:type (ex-data e))
          :resource-not-found (let [{:keys [resource-type resource-id]} (ex-data e)]
                                (log/warn (.getMessage e) resource-id)
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
          :duplicate-resource (let [{:keys [resource-type resource-key]} (ex-data e)]
                                (log/warn (.getMessage e) resource-key)
                                (ok-with-failure {:cause "resource already exists"
                                                  :resource-type resource-type
                                                  :resource-key resource-key}))
          (throw e))))))

(defn wrap-default-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/debug e)
        (ok-with-failure {:cause "unknown error" :message (.getMessage e)})))))