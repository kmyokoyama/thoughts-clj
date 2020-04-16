(ns dev
  (:require [clojure.java.io :as io]
            [datomic.client.api :as d]
            [com.stuartsierra.component :as component]
            [twitter-clj.adapter.repository.datomic :refer [make-datomic-storage]]
            [twitter-clj.application.service :refer [make-service]]))

;; Component.

;; System without API (or any driver-side).
(defn system-dev
  [_config-dev]
  (component/system-map
    :repository (make-datomic-storage)
    :service (component/using
               (make-service)
               [:repository])))

(defn start-system-dev
  ([]
   (start-system-dev {}))

  ([config-dev]
   (component/start (system-dev config-dev))))

;; Util.

(defn load-schema
  [conn resource]
  (let [m (-> resource io/resource slurp read-string)]
    (doseq [v (vals m)]
      (doseq [tx v]
        @(d/transact conn tx)))))