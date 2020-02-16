(ns twitter-clj.main
  (:require [twitter-clj.rest.handler :as handler]
            [org.httpkit.server :as server]
            [compojure.core :refer :all]
            [ring.middleware.defaults :refer :all])
  (:gen-class))

(defn -main
  [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (server/run-server (wrap-defaults #'handler/app-routes site-defaults) {:port port})
    (println (str "Running server at http://127.0.01:" port "/"))))

