(ns thoughts.adapter.http.component
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as log]
            [thoughts.adapter.http.handler :as a.http.handler])
  (:import (org.eclipse.jetty.server Server)))

(defrecord HttpServer [host port http-server service]
  component/Lifecycle
  (start [this]
    (log/info "Starting HTTP server on" (str "http://" host ":" port))
    (assoc this :http-server
                (jetty/run-jetty (a.http.handler/handler service) {:host host :port port :join? false})))

  (stop [this]
    (log/info "Stopping HTTP server" port)
    (.stop ^Server (:http-server this))
    this))

(defn make-http-controller                                  ;; Constructor.
  [host port]
  (map->HttpServer {:host host :port port}))