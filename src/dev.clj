(ns dev
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [twitter-clj.adapter.repository.datomic :refer [create-database
                                                            delete-database
                                                            make-datomic-storage
                                                            load-schema]]
            [twitter-clj.application.service :refer [make-service]]))

;; To remember:

;(require '[twitter-clj.application.core :as core])
;(require '[twitter-clj.application.port.repository :refer :all])
;(def sys (start-dev-system))
;(def conn (get-conn sys))
;(def db (get-db sys))
;(def repository (get-in sys [:repository]))

(def dev-config {:db-uri "datomic:mem://hello"})

;; System without API (or any driver-side).
(defn dev-system-map
  [config]
  (component/system-map
    :repository (make-datomic-storage (:db-uri config))
    :service (component/using
               (make-service)
               [:repository])))

(defn start-dev-system
  ([]
   (start-dev-system dev-config))

  ([config]
   (create-database (:db-uri config))
   (let [sys (component/start (dev-system-map config))
         conn (get-in sys [:repository :conn])]
     (load-schema conn "schema.edn")
     sys)))

(defn stop-dev-system
  [sys]
  (delete-database (:db-uri dev-config))
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