(ns twitter-clj.application.main
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.defaults :refer :all]
            [taoensso.timbre :as log]
            [twitter-clj.adapter.rest.component :refer [make-http-controller]]
            [twitter-clj.adapter.repository.datomic :refer [make-datomic-storage]]
            [twitter-clj.application.config :refer [system-config init-system!]]
            [twitter-clj.application.service :refer [make-service]])
  (:gen-class))

(defn system-map
  [config]
  (component/system-map
    :repository (make-datomic-storage (get-in config [:datomic :uri]))
    :service (component/using
               (make-service)
               [:repository])
    :controller (component/using
                  (make-http-controller (:http config))
                  [:service])))

(defn on-exit
  [sys]
  (fn []
    (log/info "Stopping system")
    (component/stop sys)))

(defn handle-sigint
  [on-exit sys]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable (on-exit sys))))

(defn -main
  [& _args]
  (init-system!)
  (log/info "Starting system")
  (let [sys (component/start (system-map system-config))]
    (log/info (str "Running server at http://127.0.0.1:" (get-in system-config [:http :port]) "/"))
    (handle-sigint on-exit sys)))