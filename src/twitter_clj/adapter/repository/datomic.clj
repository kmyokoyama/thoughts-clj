(ns twitter-clj.adapter.repository.datomic
  (:require [datomic.client.api :as d]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :as core])
  (:import [java.util Date UUID]
           [java.time ZonedDateTime ZoneId]))

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
  [conn tx]
  (d/transact conn {:tx-data tx}))

(defn inst->ZonedDateTime
  [inst]
  (ZonedDateTime/ofInstant (.toInstant inst) (ZoneId/systemDefault)))

(def fetch-tweets-query '[:find ?id ?user-id ?text ?created-at ?likes ?retweets ?replies
                          :in $ % ?key
                          :where
                          (get-tweet-rule ?id ?user-id ?created-at ?text ?likes ?retweets ?replies)])

(def tweet-rules '[[(get-tweet-rule ?id ?user-id ?created-at ?text ?likes ?retweets ?replies)
                    [?t :tweet/id ?id]
                    [?t :tweet/created-at ?created-at]
                    [?t :tweet/text ?text]
                    [?t :tweet/likes ?likes]
                    [?t :tweet/retweets ?retweets]
                    [?t :tweet/replies ?replies]
                    [?t :tweet/user ?u]
                    [?u :user/id ?user-id]]])

(defn- -fetch-tweets!
  [db key criteria]
  (let [k (case criteria
            :by-id '?id
            :by-user-id '?user-id)]
    (as-> (first (d/q (replace {'?key k} fetch-tweets-query)
                      db
                      tweet-rules
                      key)) $
          (apply core/->Tweet $)
          (update $ :publish-date inst->ZonedDateTime)
          (update $ :id str)
          (update $ :user-id str))))

(defn make-tweet
  [{:keys [id user-id text publish-date likes retweets replies]}]
  (let [uuid (UUID/fromString id)
        created-at (Date/from (.toInstant publish-date))
        user-uuid (UUID/fromString user-id)]
    #:tweet{:id         uuid
            :created-at created-at
            :text       text
            :likes      likes
            :retweets   retweets
            :replies    replies
            :user       [:user/id user-uuid]}))

(defn make-user
  [{:keys [id active name email username]}]
  (let [uuid (UUID/fromString id)]
    #:user{:id       uuid
           :active   active
           :name     name
           :email    email
           :username username}))

(defn update-tweet!
  [conn tweet]
  (do-transaction conn [(make-tweet tweet)]))

(defn update-user!
  [conn user]
  (do-transaction conn [(make-user user)]))

(defn fetch-tweets!                                         ;; TODO: Handle trouble paths.
  [repo key criteria]
  (let [conn (:conn repo)
        db (d/db conn)]
    (case criteria
      :by-id (-fetch-tweets! db (UUID/fromString key) criteria)
      :by-user-id (-fetch-tweets! db (UUID/fromString key) criteria))))
