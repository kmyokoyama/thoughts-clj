(ns twitter-clj.application.main
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.defaults :refer :all]
            [taoensso.timbre :as log]
            [twitter-clj.adapter.http.component :refer [make-http-controller]]
            [twitter-clj.adapter.repository.datomic :refer [make-datomic-repository]]
            [twitter-clj.application.config :refer [datomic-uri init-system! http-host http-port]]
            [twitter-clj.application.service :refer [make-service]])
  (:gen-class))

(defn- system-map
  []
  (component/system-map
    :repository (make-datomic-repository datomic-uri)
    :service (component/using
               (make-service)
               [:repository])
    :controller (component/using
                  (make-http-controller http-host http-port)
                  [:service])))

(defn- on-exit
  [sys]
  (fn []
    (log/info "Stopping system")
    (component/stop sys)))

(defn- handle-sigint
  [on-exit sys]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable (on-exit sys))))

(defn -main
  [& _args]
  (init-system!)
  (log/info "Starting system")
  (let [sys (component/start (system-map))]
    (log/info (str "Running server at http://" http-host ":" http-port "/"))
    (handle-sigint on-exit sys)))