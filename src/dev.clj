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
   (let [s (component/start (system-dev config-dev))
         c (get-in s [:repository :conn])
         d (d/db c)]
     (def sys (atom nil))
     (def conn (atom nil))
     (def db (atom nil))
     (reset! sys s)
     (reset! conn c)
     (reset! db d))))

(defn reload
  []
  (reset! conn (get-in @sys [:repository :conn]))
  (reset! db (d/db @conn)))

;; Util.

(defn load-schema
  ([]
   (load-schema @conn "schema.edn"))

  ([conn resource]
   (let [m (-> resource io/resource slurp read-string)]
     (doseq [v (vals m)]
       (doseq [tx v]
         (println tx)
         (d/transact conn {:tx-data tx}))))))

(defn find-by-eid
  [eid]
  (d/q '[:find ?attr ?v
         :in $ ?eid
         :where
         [?eid ?a ?v]
         [?a :db/ident ?attr]] @db eid))