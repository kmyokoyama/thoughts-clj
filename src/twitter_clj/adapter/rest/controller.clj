(ns twitter-clj.adapter.rest.controller
  (:require [clojure.data.json :as json]
            [com.stuartsierra.component :as component]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.server :as server]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [taoensso.timbre :as log]
            [twitter-clj.application.app :as app]))

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
    (respond-with {})))
    ;(respond-with updated-tweet)))

(defn app-routes
  [app]
  (compojure.core/routes
    (POST "/user" req (add-user req app))
    (GET "/users" req (get-users req app))
    (POST "/tweet" req (add-tweet req app))
    (GET "/tweets" req (get-tweets-by-user req app))
    (PATCH "/tweet/like" req (like-tweet req app))
    (route/not-found "Error, page not found!")))

(defn handler
  [app]
  (-> (app-routes app)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-defaults api-defaults)))

(defrecord HttpServer [http-server app server-config]
  component/Lifecycle
  (start [this]
    (log/info "Starting HTTP server.")
    (let [port (:port server-config)]
      (assoc this :http-server
                  (server/run-server (handler app) {:port port}))))

  (stop [this]
    (log/info "Stopping HTTP server.")
    (let [stop-fn (:http-server this)]
      (stop-fn)
      this)))

(defn make-http-controller ;; Constructor.
  [server-config]
  (map->HttpServer {:server-config server-config}))