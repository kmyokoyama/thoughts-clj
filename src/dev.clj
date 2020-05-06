(ns dev
  (:require [clojure.java.io :as io]
            [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [twitter-clj.adapter.repository.datomic :refer [create-database
                                                            delete-database
                                                            make-datomic-storage
                                                            load-schema]]
            [twitter-clj.application.service :refer [make-service]]))

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
   (component/start (dev-system-map config))))

(defn stop-dev-system
  [sys]
  (delete-database (:db-uri config))
  (component/stop sys))

(defn get-conn
  [sys]
  (get-in sys [:repository :conn]))

(defn get-db
  [sys]
  (-> sys (get-conn) (d/db)))

(defn find-by-eid
  [eid]
  (d/q '[:find ?attr ?v
         :in $ ?eid
         :where
         [?eid ?a ?v]
         [?a :db/ident ?attr]] @db eid))