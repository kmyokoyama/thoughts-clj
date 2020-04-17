(ns dev
  (:require [clojure.java.io :as io]
            [datomic.client.api :as d]
            [com.stuartsierra.component :as component]
            [twitter-clj.adapter.repository.datomic :refer [make-datomic-storage]]
            [twitter-clj.application.service :refer [make-service]])
  (:import [java.util Date UUID]))

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
        (println tx)
        (d/transact conn {:tx-data tx})))))

(defn make-tweet
  [id created-at username]
  (let [uuid (UUID/fromString id)
        created-date (Date/from (.toInstant created-at))]
    {:tweet/id uuid
     :tweet/created-at created-date
     :tweet/text "This is a new tweet"
     :tweet/likes 0
     :tweet/retweets 0
     :tweet/replies 0
     :tweet/user [:user/email username]}))
