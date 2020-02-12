(ns twitter-clj.rest.handler
  (:require [twitter-clj.main :as main]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defroutes app-routes
           (GET "/" [] main/simple-body-page)
           (GET "/user" [] main/add-user)
           (GET "/users" [] main/get-users)
           (GET "/tweet" [] main/add-tweet)
           (GET "/tweets" [] main/get-tweets-by-user)
           (route/not-found "Error, page not found!"))

(def handler
  (wrap-defaults #'app-routes site-defaults))