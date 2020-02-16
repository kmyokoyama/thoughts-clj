(ns twitter-clj.main
  (:require [twitter-clj.rest.handler :refer [handler]]
            [org.httpkit.server :as server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer :all]
            [clojure.data.json :as json]
            [twitter-clj.operations :as app])
  (:gen-class))

(defn -main
  [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (server/run-server (wrap-defaults #'handler/app-routes site-defaults) {:port port})
    (println (str "Running server at http://127.0.01:" port "/"))))

