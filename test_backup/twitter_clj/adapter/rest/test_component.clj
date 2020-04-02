(ns twitter-clj.adapter.rest.test_component
  (:require [com.stuartsierra.component :as component]
            [twitter-clj.adapter.rest.component :refer [make-http-controller]]
            [twitter-clj.adapter.repository.in-mem :refer [make-in-mem-storage]]
            [twitter-clj.application.service :refer [make-service]]))

(defn- test-system
  [system-config]
  (component/system-map
    :repository (make-in-mem-repository)
    :app (component/using
           (make-app)
           [:repository])
    :controller (component/using
                  (make-http-controller (:server-config system-config))
                  [:app])))

(defn start-test-system!
  [system-config]
  (component/start (test-system system-config)))

(defn stop-test-system! [system]
  (component/stop system))