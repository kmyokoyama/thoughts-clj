(ns thoughts.application.main
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.defaults :refer :all]
            [taoensso.timbre :as log]
            [thoughts.adapter.cache.in-mem :as a.cache.in-mem]
            [thoughts.adapter.config.simple-config :as a.config.simple-config]
            [thoughts.adapter.http.component :as a.http.component]
            [thoughts.adapter.repository.in-mem :as a.repository.in-mem]
            [thoughts.application.service :as service])
  (:gen-class))

(defn- system-map
  []
  (component/system-map
   :config (a.config.simple-config/make-simple-config)
   :repository (a.repository.in-mem/make-in-mem-repository)
   :cache (a.cache.in-mem/make-in-mem-cache)
   :service (component/using
             (service/make-service)
             [:repository :cache])
   :controller (component/using
                (a.http.component/make-http-controller)
                [:config :service])))

(defn- on-exit
  [sys]
  (fn []
    (log/info "Stopping system")
    (component/stop sys)))

(defn- handle-sigint
  [on-exit sys]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable (on-exit sys))))

(def ^:private timbre-config {:timestamp-opts {:pattern "yyyy-MM-dd'T'HH:mm:ss.SSSX"}
                              :output-fn      (fn [{:keys [timestamp_ level hostname_ msg_]}]
                                                (let [level (clojure.string/upper-case (name level))
                                                      timestamp (force timestamp_)
                                                      hostname (force hostname_)
                                                      msg (force msg_)]
                                                  (str "[" level "] " timestamp " @" hostname " - " msg)))})

(defn init-system!
  []
  (log/merge-config! timbre-config))

(defn -main
  [& _args]
  (init-system!)
  (log/info "Starting system")
  (let [sys (component/start (system-map))]
    (handle-sigint on-exit sys)))