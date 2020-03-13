(ns twitter-clj.application.main
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.defaults :refer :all]
            [taoensso.timbre :as log]
            [twitter-clj.adapter.rest.handler :refer [make-http-controller]]
            [twitter-clj.adapter.storage.in-mem :refer [make-in-mem-storage]]
            [twitter-clj.application.app :refer [make-app]])
  (:gen-class))

(defn system
  [system-config]
  (component/system-map
    :storage (make-in-mem-storage)
    :app (component/using
           (make-app)
           [:storage])
    :controller (component/using
                  (make-http-controller (:server-config system-config))
                  [:app])))

(defn on-exit
  [system]
  (fn []
    (log/info "Stopping system...")
    (component/stop system)))

(defn handle-sigint
  [on-exit system]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable (on-exit system))))

(defn -main
  [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (log/info "Starting system...")
    (let [system (component/start (system {:server-config {:port port}}))]
      (log/info (str "Running server at http://127.0.01:" port "/"))
      (handle-sigint on-exit system))))