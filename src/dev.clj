(ns dev
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [twitter-clj.application.config :refer [datomic-uri]]
            [twitter-clj.adapter.repository.datomic :refer [delete-database
                                                            make-datomic-repository
                                                            load-schema]]
            [twitter-clj.application.service :refer [make-service]]))

;; To remember:

;(require '[twitter-clj.application.core :as core])
;(require '[twitter-clj.application.port.repository :refer :all])
;(def sys (start-dev-system))
;(def conn (get-conn sys))
;(def db (get-db sys))
;(def repository (get-in sys [:repository]))

;; System without API (or any driver-side).
(defn dev-system-map
  []
  (component/system-map
    :repository (make-datomic-repository datomic-uri)
    :service (component/using
               (make-service)
               [:repository])))

(defn start-dev-system
  []
  (let [sys (component/start (dev-system-map))
        conn (get-in sys [:repository :conn])]
    (load-schema conn "schema.edn")
    sys))

(defn stop-dev-system
  [sys]
  (delete-database datomic-uri)
  (component/stop sys))

(defn get-conn
  [sys]
  (get-in sys [:repository :conn]))

(defn get-db
  [sys]
  (-> sys (get-conn) (d/db)))

(defn find-by-eid
  [db eid]
  (d/q '[:find ?attr ?v
         :in $ ?eid
         :where
         [?eid ?a ?v]
         [?a :db/ident ?attr]] db eid))