(ns twitter-clj.rest.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
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
  (let [name (get-parameter req :name)
        email (get-parameter req :email)
        nickname (get-parameter req :nickname)
        user (app/add-user name email nickname)]
    (respond-with user)))

(defn get-users
  [_req]
  (let [users (app/get-users)]
    (respond-with users)))

(defn add-tweet
  [req]
  (let [user-id (get-parameter req :user-id)
        text (get-parameter req :text)
        tweet (app/add-tweet user-id text)]
    (respond-with tweet)))

(defn get-tweets-by-user
  [req]
  (let [user-id (get-parameter req :user-id)
        tweets (app/get-tweets-by-user user-id)]
    (respond-with tweets)))

(defroutes app-routes
           (GET "/user" [] add-user)
           (GET "/users" [] get-users)
           (GET "/tweet" [] add-tweet)
           (GET "/tweets" [] get-tweets-by-user)
           (route/not-found "Error, page not found!"))

(def handler
  (wrap-defaults #'app-routes site-defaults))