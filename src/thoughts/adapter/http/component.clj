(ns thoughts.adapter.http.component
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as log]
            [thoughts.adapter.http.handler :as a.http.handler]
            [thoughts.port.config :as p.config])
  (:import (org.eclipse.jetty.server Server)))

(defrecord HttpServer [http-server config service]
  component/Lifecycle
  (start [this]
    (let [host (p.config/value-of! config :http-host)
          port (p.config/value-of! config :http-port)]
      (log/info "Starting HTTP server on" (str "http://" host ":" port))
      (assoc this
             :http-server
             (jetty/run-jetty (a.http.handler/handler config service) {:host host :port port :join? false}))))

  (stop [this]
    (log/info "Stopping HTTP server")
    (.stop ^Server (:http-server this))
    this))

(defn make-http-controller                                  ;; Constructor.
  []
  (map->HttpServer {}))