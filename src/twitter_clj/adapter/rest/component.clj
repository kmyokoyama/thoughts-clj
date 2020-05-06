(ns twitter-clj.adapter.rest.component
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as server]
            [taoensso.timbre :as log]
            [twitter-clj.adapter.rest.handler :refer [handler]]))

(defrecord HttpServer [port http-server service]
  component/Lifecycle
  (start [this]
    (log/info "Starting HTTP server at port" port)
    (assoc this :http-server
                (server/run-server (handler service) {:port port})))

  (stop [this]
    (log/info "Stopping HTTP server" port)
    (let [stop-fn (:http-server this)]
      (stop-fn)
      this)))

(defn make-http-controller ;; Constructor.
  [port]
  (map->HttpServer {:port port}))