(ns twitter-clj.application.main
  (:require [twitter-clj.adapter.rest.controller :refer [make-http-controller]]
            [twitter-clj.adapter.storage.in-mem :refer [make-in-mem-storage]]
            [twitter-clj.application.app :refer [make-app]]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as server]
            [ring.middleware.defaults :refer :all])
  (:gen-class))

(defn system
  []
  (component/system-map
    :storage (make-in-mem-storage)
    :app (component/using (make-app) [:storage])
    :controller (component/using (make-http-controller) [:app])))

(defn -main
  [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (component/start (system))
    (println (str "Running server at http://127.0.01:" port "/"))))

