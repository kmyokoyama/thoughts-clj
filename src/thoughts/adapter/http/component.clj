(ns thoughts.adapter.http.component
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http.server]
            [taoensso.timbre :as log]
            [thoughts.adapter.http.handler :as a.http.handler]))

(defrecord HttpServer [host port http-server service]
  component/Lifecycle
  (start [this]
    (log/info "Starting HTTP server on" (str "http://" host ":" port))
    (assoc this :http-server
                (http.server/run-server (a.http.handler/handler service) {:ip host :port port})))

  (stop [this]
    (log/info "Stopping HTTP server" port)
    (let [stop-fn (:http-server this)]
      (stop-fn)
      this)))

(defn make-http-controller                                  ;; Constructor.
  [host port]
  (map->HttpServer {:host host :port port}))