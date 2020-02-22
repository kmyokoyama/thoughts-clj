(ns twitter-clj.rest.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [twitter-clj.operations :as app]))

(defn get-parameter
  [req param]
  (param (:params req)))

(defn ok-json
  [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body body})

(def success-response
  {:status "success"})

(def failure-response
  {:status "failure"})

(defn add-response-info
  [info]
  (assoc success-response :result info))

(defn to-json
  [r]
  (json/write-str r :value-fn app/value-writer))

(def respond-with (comp ok-json to-json add-response-info))

(defn add-user
  [req]
  (let [{:keys [name email nickname]} (:body req)
        user (app/add-user name email nickname)]
    (respond-with user)))

(defn get-users
  [_req]
  (let [users (app/get-users)]
    (respond-with users)))

(defn add-tweet
  [req]
  (let [{:keys [user-id text]} (:body req)
        tweet (app/add-tweet user-id text)]
    (respond-with tweet)))

(defn get-tweets-by-user
  [req]
  (let [user-id (get-parameter req :user-id)
        tweets (app/get-tweets-by-user user-id)]
    (respond-with tweets)))

(defn like-tweet
  [req]
  (let [tweet-id (get-parameter req :tweet-id)
        updated-tweet (app/like tweet-id)]
    (println tweet-id)
    (respond-with {})))
    ;(respond-with updated-tweet)))

(defroutes app-routes
           (POST "/user" [] add-user)
           (GET "/users" [] get-users)
           (POST "/tweet" [] add-tweet)
           (GET "/tweets" [] get-tweets-by-user)
           (PATCH "/tweet/like" [] like-tweet)
           (route/not-found "Error, page not found!"))

(def handler
  (-> #'app-routes
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-defaults api-defaults)))