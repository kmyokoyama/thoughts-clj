(ns thoughts.application.main
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.defaults :refer :all]
            [taoensso.timbre :as log]
            [thoughts.adapter.cache.in-mem :refer [make-in-mem-cache]]
            [thoughts.adapter.cache.redis :refer [make-redis-cache]]
            [thoughts.adapter.http.component :refer [make-http-controller]]
            [thoughts.adapter.repository.datomic :refer [make-datomic-repository]]
            [thoughts.adapter.repository.in-mem :refer [make-in-mem-repository]]
            [thoughts.application.config :refer [datomic-uri http-host http-port init-system! redis-uri]]
            [thoughts.application.service :refer [make-service]])
  (:gen-class))

(defn- system-map
  []
  (component/system-map
    :repository (make-datomic-repository datomic-uri)
    :cache (make-in-mem-cache)
    :service (component/using
               (make-service)
               [:repository :cache])
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