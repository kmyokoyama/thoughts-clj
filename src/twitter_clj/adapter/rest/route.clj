(ns twitter-clj.adapter.rest.route
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [twitter-clj.adapter.rest.config :refer [path-prefix]]
            [twitter-clj.adapter.rest.handler :refer :all]))

(defn app-routes
  [service]
  (compojure.core/routes
    ;; User API.
    (POST (path-prefix "/user") req (add-user req service)) ;; TODO: Change it to PUT based on user's email.
    (GET (path-prefix "/user/:user-id") req (get-user-by-id req service))

    ;; Tweet API.
    (GET (path-prefix "/tweet/:tweet-id") req (get-tweet-by-id req service))
    (GET (path-prefix "/tweet") req (get-tweets-by-user req service))
    (GET (path-prefix "/tweet/:tweet-id/retweet") req (get-retweets-by-tweet-id req service))
    (GET (path-prefix "/retweet/:retweet-id") req (get-retweet-by-id req service))
    (POST (path-prefix "/tweet") req (add-tweet req service))
    (POST (path-prefix "/tweet/:tweet-id/reply") req (add-reply req service))
    (POST (path-prefix "/tweet/:tweet-id/retweet") req (add-retweet req service))
    (POST (path-prefix "/tweet/:tweet-id/retweet-comment") req (add-retweet-with-comment req service))
    (POST (path-prefix "/tweet/:tweet-id/react") req (tweet-react req service))

    ;; Default.
    (route/not-found "Error, page not found!")))

(defn handler
  [service]
  (-> (app-routes service)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      wrap-resource-not-found
      wrap-duplicate-resource
      wrap-default-exception
      (wrap-defaults api-defaults)))