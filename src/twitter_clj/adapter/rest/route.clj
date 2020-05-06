(ns twitter-clj.adapter.rest.route
  (:require [buddy.auth.middleware :refer [wrap-authentication]]
            [compojure.core :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body]]
            [twitter-clj.adapter.rest.config :refer [path-prefix jws-backend]]
            [twitter-clj.adapter.rest.handler :refer :all]))

(defn public-routes
  [service]
  (compojure.core/routes
    (POST (path-prefix "/login") req (login req service))
    (POST (path-prefix "/signup") req (signup req service))))

(defn user-routes
  [service]
  (compojure.core/routes
    (POST (path-prefix "/logout") req (logout req service))
    (GET (path-prefix "/user/:user-id") req (get-user-by-id req service))
    (GET (path-prefix "/user/:user-id/tweets") req (get-tweets-by-user req service))
    (GET (path-prefix "/tweet/:tweet-id") req (get-tweet-by-id req service))
    (GET (path-prefix "/tweet/:tweet-id/replies") req (get-replies-by-tweet-id req service))
    (GET (path-prefix "/tweet/:tweet-id/retweets") req (get-retweets-by-tweet-id req service))
    (GET (path-prefix "/retweet/:retweet-id") req (get-retweet-by-id req service))
    (POST (path-prefix "/tweet") req (add-tweet req service))
    (POST (path-prefix "/tweet/:tweet-id/reply") req (add-reply req service))
    (POST (path-prefix "/tweet/:tweet-id/retweet") req (add-retweet req service))
    (POST (path-prefix "/tweet/:tweet-id/retweet-comment") req (add-retweet-with-comment req service))
    (POST (path-prefix "/tweet/:tweet-id/react") req (tweet-react req service))))

(defn handler
  [service]
  (-> (compojure.core/routes
        (public-routes service)
        (-> (user-routes service)
            (wrap-authenticated service)
            (wrap-authentication jws-backend)))
      (wrap-service-exception)
      (wrap-default-exception)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-defaults api-defaults)))
