(ns thoughts.adapter.config.simple-config
  (:require [com.stuartsierra.component :as component]
            [environ.core :as environ]
            [taoensso.timbre :as log]
            [thoughts.port.config :as p.config]))

(defn ^:private ->keyword
  ([s]
   (-> s (subs 1) keyword))

  ([s default]
   (try
     (->keyword s)
     (catch Exception _ default))))

(defn ^:private ->int
  ([s]
   (Integer/parseInt s))

  ([s default]
   (try
     (->int s)
     (catch NumberFormatException _ default))))

(def defaults
  {:http-port 3000
   :test-mode :in-mem})

(defn get-offline!
  [key]
  (or (environ/env key) (get defaults key)))

(defrecord SimpleConfig []
  component/Lifecycle
  (start
    [config]
    (log/info "Starting simple config")
    config)

  (stop
    [config]
    (log/info "Stopping simple config")
    config)

  p.config/Config
  (value-of!
    [_config key]
    (or (environ/env key) (get defaults key))))

(defn make-simple-config
  []
  (map->SimpleConfig {}))