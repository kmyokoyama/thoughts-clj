(ns twitter-clj.adapter.rest.controller
  (:require [twitter-clj.application.app :as app]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as server]))

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
  [req app]
  (let [{:keys [name email nickname]} (:body req)
        user (app/add-user app name email nickname)]
    (respond-with user)))

(defn get-users
  [_req app]
  (let [users (app/get-users app)]
    (respond-with users)))

(defn add-tweet
  [req app]
  (let [{:keys [user-id text]} (:body req)
        tweet (app/add-tweet app user-id text)]
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
    (println tweet-id)
    (respond-with {})))
    ;(respond-with updated-tweet)))

;(defroutes app-routes
;           (POST "/user" [] add-user)
;           (GET "/users" [] get-users)
;           (POST "/tweet" [] add-tweet)
;           (GET "/tweets" [] get-tweets-by-user)
;           (PATCH "/tweet/like" [] like-tweet)
;           (route/not-found "Error, page not found!"))

(defn hello-world
  [req app]
  (let [name (get-parameter req :name)]
    (app/hello-world app name)))

(defn app-routes
  [app]
  (compojure.core/routes
    (GET "/" req (hello-world req app))
    (POST "/user" req (add-user req app))))

(defn handler
  [app]
  (-> (app-routes app)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-defaults api-defaults)))

(defrecord HttpServer [http-server app]
  component/Lifecycle
  (start [this]
    (println "Starting HTTP server.")
    (server/run-server (handler app) {:port 3000}) ;; TODO: Pass configuration.
    this)

  (stop [this]
    (println "Stopping HTTP server.")
    this))

(defn make-http-controller
  []
  (map->HttpServer {}))