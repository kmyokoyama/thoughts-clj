(ns dev
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [puget.printer :as puget]
            [thoughts.adapter.repository.datomic :refer [delete-database
                                                         load-schema
                                                         make-datomic-repository]]
            [thoughts.application.service :refer [make-service]]))

(defn look-reader
  [x]
  (let [result (eval x)]
    (puget/cprint result)
    result))

(defmacro look [body] `(let [result# ~body] (puget/cprint result#) result#))

;; To remember:

;(def sys (start-dev-system))
;(def conn (get-conn sys))
;(def db (get-db sys))
;(def repository (get-in sys [:repository]))
;(require '[thoughts.application.core :as core])
;(require '[thoughts.port.repository :refer :all])
;(require '[thoughts.adapter.repository.datomic :as datomic])
;(require '[taoensso.carmine :as car :refer [wcar])
;(def first-user (core/new-user "First User" "first.user@gmail.com" "first.user"))
;(def second-user (core/new-user "second User" "second.user@gmail.com" "second.user"))
;(def third-user (core/new-user "third User" "third.user@gmail.com" "third.user"))
;(def first-uuid (:id first-user))
;(def second-uuid (:id second-user))
;(def third-uuid (:id third-user))
;(update-user! repository first-user)
;(update-user! repository second-user)
;(update-user! repository third-user)
;(update-follow! repository first-user second-user)
;(update-follow! repository first-user third-user)
;(update-follow! repository second-user first-user)
;(def redis-conn {:pool {} :uri "redis://localhost:6379"})
;(wcar redis-conn (car/ping))

;; System without API (or any driver-side).
(defn dev-system-map
  []
  (component/system-map
   :repository (make-datomic-repository)
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