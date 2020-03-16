(ns twitter-clj.adapter.rest.handler
  (:require [taoensso.timbre :as log]
            [twitter-clj.application.app :as app]
            [twitter-clj.application.util :refer [success error process]]
            [twitter-clj.adapter.rest.util :refer [get-parameter get-from-body
                                                   ok-with-success
                                                   ok-with-failure
                                                   created]]))

(defn add-user
  "This will be moved to user management API in the future."
  [req app]
  (let [{:keys [name email nickname]} (:body req)
        user (app/add-user app name email nickname)
        user-info (str name " @" nickname " [" email"]")]
    (log/info "Received request to add user" user-info)
    (created user)))

(defn get-user-by-id
  [req app]
  (let [user-id (get-parameter req :user-id)
        user (app/get-user-by-id app user-id)]
    (log/info "Received request to get user with id" user-id)
    (ok-with-success user)))

(defn add-tweet
  [req app]
  (let [{:keys [user-id text]} (:body req)
        {:keys [tweet]} (app/add-tweet app user-id text)]
    (log/info "Received request to add new tweet from user" user-id)
    (created tweet)))

(defn get-tweet-by-id
  [req app]
  (let [tweet-id (get-parameter req :tweet-id)
        tweet (app/get-tweet-by-id app tweet-id)]
    (log/info "Received request to get tweet with id" tweet-id)
    (ok-with-success tweet)))

(defn get-tweets-by-user
  [req app]
  (let [user-id (get-parameter req :user-id)
        tweets (app/get-tweets-by-user app user-id)]
    (log/info "Received request to get tweets from user" user-id)
    (ok-with-success tweets)))

(defn- like-tweet
  [tweet-id app]
  (log/info "Received request of like of tweet" tweet-id)
  (-> (process (app/like app tweet-id)
               ok-with-success
               (fn [_op] (ok-with-failure {:cause "Tweet not found" :id tweet-id})))
      (:result)))

(defn tweet-action
  [req app]
  (let [tweet-id (get-parameter req :tweet-id)
        action (keyword (get-from-body req :action))]
    (case action
      :like (like-tweet tweet-id app))))