(ns twitter-clj.adapter.rest.route
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [twitter-clj.adapter.rest.handler :refer :all]))

(defn app-routes
  [app]
  (compojure.core/routes
    ;; User API.
    (GET "/user" req (get-users req app))
    (POST "/user" req (add-user req app))
    (GET "/users" req (get-users req app)) ;; TODO: Remove it.

    ;; Tweet API.
    (GET "/tweets" req (get-tweets-by-user req app)) ;; TODO: Remove it.
    (GET "/tweet" req (get-tweets-by-user req app))
    (POST "/tweet" req (add-tweet req app))
    (POST "/tweet/:tweet-id" req (like-tweet req app)) ;; TODO: Turn into a dispatch function.

    ;; Default.
    (route/not-found "Error, page not found!")))

(defn handler
  [app]
  (-> (app-routes app)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-defaults api-defaults)))