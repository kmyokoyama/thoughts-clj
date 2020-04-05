(ns twitter-clj.application.main
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.defaults :refer :all]
            [taoensso.timbre :as log]
            [outpace.config :refer [defconfig]]
            [twitter-clj.adapter.rest.component :refer [make-http-controller]]
            [twitter-clj.adapter.repository.in-mem :refer [make-in-mem-storage]]
            [twitter-clj.application.config :refer [system-config init-system!]]
            [twitter-clj.application.service :refer [make-service]])
  (:gen-class))

(defn system
  [system-config]
  (component/system-map
    :repository (make-in-mem-storage)
    :service (component/using
               (make-service)
               [:repository])
    :controller (component/using
                  (make-http-controller (:http system-config))
                  [:service])))

(defn on-exit
  [system]
  (fn []
    (log/info "Stopping system")
    (component/stop system)))

(defn handle-sigint
  [on-exit system]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable (on-exit system))))

(defn -main
  [& _args]
  (init-system!)
  (log/info "Starting system")
  (let [system (component/start (system system-config))]
    (log/info (str "Running server at http://127.0.0.1:" (get-in system-config [:http :port]) "/"))
    (handle-sigint on-exit system)))