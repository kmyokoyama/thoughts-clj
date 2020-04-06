(ns twitter-clj.adapter.rest.handler
  (:require [taoensso.timbre :as log]
            [twitter-clj.application.service :as service]
            [twitter-clj.adapter.rest.hateoas :as hateoas]
            [twitter-clj.adapter.rest.util :refer [get-parameter get-from-body
                                                   ok-with-success
                                                   ok-with-failure
                                                   created
                                                   f
                                                   f-id]])
  (:import [clojure.lang ExceptionInfo]))

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
    (log/info "Get user" (f user))
    (ok-with-success user)))

(defn add-tweet
  [req service]
  (let [{:keys [user-id text]} (:body req)
        tweet (service/add-tweet service user-id text)]
    (log/info "Add tweet" (f tweet) "from user" (f-id user-id))
    (created (hateoas/add-links :tweet req tweet))))

(defn get-tweet-by-id
  [req service]
  (let [tweet-id (get-parameter req :tweet-id)
        tweet (service/get-tweet-by-id service tweet-id)]
    (log/info "Get tweet" (f tweet))
    (ok-with-success (hateoas/add-links :tweet req tweet))))

(defn get-tweets-by-user
  [req service]
  (let [user-id (get-parameter req :user-id)
        tweets (service/get-tweets-by-user service user-id)]
    (log/info "Get tweets from user" (f-id user-id))
    (ok-with-success (map (partial hateoas/add-links :tweet req) tweets))))

(defn add-reply
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id text]} (:body req)
        reply (service/add-reply service user-id text source-tweet-id)]
    (log/info "Reply tweet" (f-id source-tweet-id) "from user" (f-id user-id))
    (created (hateoas/add-links :reply req source-tweet-id reply))))

(defn add-retweet
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        user-id (get-in req [:body :user-id])
        retweet (service/retweet service user-id source-tweet-id)]
    (log/info "Retweet tweet" (f-id source-tweet-id) "from user" (f-id user-id))
    (created (hateoas/add-links :retweet req source-tweet-id retweet))))

(defn add-retweet-with-comment
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        {:keys [user-id comment]} (:body req)
        retweet (service/retweet-with-comment service user-id comment source-tweet-id)]
    (log/info "Retweet tweet" (f-id source-tweet-id) "from user" (f-id user-id))
    (created (hateoas/add-links :retweet req source-tweet-id retweet))))

(defn get-retweet-by-id
  [req service]
  (let [retweet-id (get-parameter req :retweet-id)
        retweet (service/get-retweet-by-id service retweet-id)]
    (log/info "Get retweet" (f-id retweet-id))
    (ok-with-success (hateoas/add-links :retweet req (:source-tweet-id retweet) retweet))))

(defn get-retweets-by-tweet-id
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        retweets (service/get-retweets-by-tweet-id service source-tweet-id)]
    (log/info "Get retweets of tweet" (f-id source-tweet-id))
    (ok-with-success (map (partial hateoas/add-links :retweet req source-tweet-id) retweets))))

(defn get-replies-by-tweet-id
  [req service]
  (let [source-tweet-id (get-parameter req :tweet-id)
        replies (service/get-replies-by-tweet-id service source-tweet-id)]
    (log/info "Get replies of tweet" (f-id source-tweet-id))
    (ok-with-success (map (partial hateoas/add-links :reply req source-tweet-id) replies))))

(defn- like-tweet
  [req service user-id tweet-id]
  (log/info "Like tweet" (f-id tweet-id))
  (try
    (->> (service/like service user-id tweet-id)
         (hateoas/add-links :tweet req)
         (ok-with-success))))

(defn- unlike-tweet
  [req service user-id tweet-id]
  (log/info "Unlike tweet" (f-id tweet-id))
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

(defn wrap-service-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [failure-info (ex-data e)]
          (if (and (:type failure-info) (:subject failure-info))
            (do (log/warn "Failure - " (.getMessage e))
                (-> failure-info (format-failure-info) (ok-with-failure)))
            (throw e)))))))

(defn wrap-default-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/debug e)
        (ok-with-failure {:type "unknown error" :cause (.getMessage e)})))))