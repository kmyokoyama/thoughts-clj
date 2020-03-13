(ns twitter-clj.adapter.rest.handler
  (:require [taoensso.timbre :as log]
            [twitter-clj.application.app :as app]
            [twitter-clj.adapter.rest.util :refer [get-parameter respond-with]]))

(defn add-user
  [req app]
  (let [{:keys [name email nickname]} (:body req)
        user (app/add-user app name email nickname)]
    (let [{:keys [name email nickname]} user
          user-info (str name " @" nickname " [" email"]")]
      (log/info "Received request to add user:" user-info))
    (respond-with user)))

(defn get-users
  [_req app]
  (let [users (app/get-users app)]
    (respond-with users)))

(defn add-tweet
  [req app]
  (let [{:keys [user-id text]} (:body req)
        {:keys [tweet]} (app/add-tweet app user-id text)]
    (respond-with tweet)))

(defn get-tweets-by-user
  [req app]
  (let [user-id (get-parameter req :user-id)
        tweets (app/get-tweets-by-user app user-id)]
    (respond-with tweets)))

(defn like-tweet
  [req app]
  (let [tweet-id (get-parameter req :tweet-id)
        updated-tweet (app/like app tweet-id)]
    (respond-with {})))
    ;(respond-with updated-tweet)))