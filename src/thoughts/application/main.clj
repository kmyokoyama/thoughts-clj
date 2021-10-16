(ns thoughts.application.main
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.defaults :refer :all]
            [taoensso.timbre :as log]
            [thoughts.adapter.cache.in-mem :as a.cache.in-mem]
            [thoughts.adapter.http.component :as a.http.component]
            [thoughts.adapter.repository.datomic :as a.repository.datomic]
            [thoughts.adapter.repository.in-mem :as a.repository.in-mem]
            [thoughts.application.config :as config]
            [thoughts.application.service :as service])
  (:gen-class))

(defn- system-map
  []
  (component/system-map
   :repository (a.repository.in-mem/make-in-mem-repository)
   :cache (a.cache.in-mem/make-in-mem-cache)
   :service (component/using
             (service/make-service)
             [:repository :cache])
   :controller (component/using
                (a.http.component/make-http-controller config/http-host config/http-port)
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
  (config/init-system!)
  (log/info "Starting system")
  (let [sys (component/start (system-map))]
    (log/info (str "Running server at http://" config/http-host ":" config/http-port "/"))
    (handle-sigint on-exit sys)))