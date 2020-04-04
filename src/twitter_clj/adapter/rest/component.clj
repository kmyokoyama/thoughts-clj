(ns twitter-clj.adapter.rest.component
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as server]
            [taoensso.timbre :as log]
            [twitter-clj.adapter.rest.route :refer [handler]]))

(defrecord HttpServer [http-server service http-config]
  component/Lifecycle
  (start [this]
    (log/info "Starting HTTP server")
    (let [port (:port http-config)]
      (assoc this :http-server
                  (server/run-server (handler service) {:port port}))))

  (stop [this]
    (log/info "Stopping HTTP server")
    (let [stop-fn (:http-server this)]
      (stop-fn)
      this)))

(defn make-http-controller ;; Constructor.
  [http-config]
  (map->HttpServer {:http-config http-config}))