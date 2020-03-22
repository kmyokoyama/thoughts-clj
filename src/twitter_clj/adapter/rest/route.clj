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
    (GET "/tweet/:tweet-id/retweet" req (get-retweets-by-tweet-id req app))
    (GET "/retweet/:retweet-id" req (get-retweet-by-id req app))
    (POST "/tweet" req (add-tweet req app))
    (POST "/tweet/:tweet-id/reply" req (add-reply req app))
    (POST "/tweet/:tweet-id/retweet" req (add-retweet req app))
    (POST "/tweet/:tweet-id/retweet-comment" req (add-retweet-with-comment req app))
    (POST "/tweet/:tweet-id/react" req (tweet-react req app))

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