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
    (POST "/user" req (add-user req app)) ;; TODO: Change it to PUT based on user's email.
    (GET "/user/:user-id" req (get-user-by-id req app))

    ;; Tweet API.
    (GET "/tweet/:tweet-id" req (get-tweet-by-id req app))
    (GET "/tweet" req (get-tweets-by-user req app))
    (POST "/tweet" req (add-tweet req app))
    (POST "/tweet/:tweet-id" req (tweet-action req app))

    ;; Default.
    (route/not-found "Error, page not found!")))

(defn handler
  [app]
  (-> (app-routes app)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      wrap-resource-not-found
      wrap-duplicate-resource
      wrap-default-exception
      (wrap-defaults api-defaults)))