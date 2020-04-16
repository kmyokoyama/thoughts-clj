(ns twitter-clj.adapter.repository.datomic
  (:require [datomic.client.api :as d]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(def cfg {:server-type        :peer-server
          :access-key         "myaccesskey"
          :secret             "mysecret"
          :endpoint           "localhost:8998"
          :validate-hostnames false})

(defrecord DatomicStorage [conn]
  component/Lifecycle
  (start
    [this]
    (log/info "Starting Datomic storage")
    (let [connection (d/connect (d/client cfg) {:db-name "hello"})]
      (assoc this :conn connection)))

  (stop
    [_this]
    (log/info "Stopping Datomic storage")))

(defn make-datomic-storage
  []
  (->DatomicStorage {}))

(defn do-transaction
  [repository tx]
  (d/transact (:conn repository) {:tx-data tx}))
