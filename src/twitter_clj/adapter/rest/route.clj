(ns twitter-clj.adapter.rest.route
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [twitter-clj.adapter.rest.handler :refer :all]))

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