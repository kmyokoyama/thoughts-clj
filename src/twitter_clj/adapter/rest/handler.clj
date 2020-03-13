(ns twitter-clj.adapter.rest.handler
  (:require [taoensso.timbre :as log]
            [twitter-clj.application.app :as app]
            [twitter-clj.adapter.rest.util :refer [get-parameter respond-with]]))

(defn add-user
  "This will be moved to user management API in the future."
  [req app]
  (let [{:keys [name email nickname]} (:body req)
        user (app/add-user app name email nickname)
        user-info (str name " @" nickname " [" email"]")]
    (log/info "Received request to add user:" user-info)
    (respond-with user)))

(defn get-users
  "This will be moved to user management API in the future."
  [_req app]
  (log/info "Received request to get all users")
  (let [users (app/get-users app)]
    (respond-with users)))

(defn add-tweet
  [req app]
  (let [{:keys [user-id text]} (:body req)
        {:keys [tweet]} (app/add-tweet app user-id text)]
    (log/info "Received request too add new tweet from user" user-id)
    (respond-with tweet)))

(defn get-tweets-by-user
  [req app]
  (let [user-id (get-parameter req :user-id)
        tweets (app/get-tweets-by-user app user-id)]
    (log/info "Received request to get tweets from user" user-id)
    (respond-with tweets)))

(defn like-tweet
  [req app]
  (let [tweet-id (get-parameter req :tweet-id)
        updated-tweet (app/like app tweet-id)]
    (log/info "Received request of like of tweet" tweet-id "from user") ;; Add user-id.
    (respond-with {})))
    ;(respond-with updated-tweet)))