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

(def tweet-rules '[[(get-tweet-rule ?id ?user-id ?text ?created-at ?likes ?retweets ?replies)
                    [?t :tweet/id ?id]
                    [?t :tweet/created-at ?created-at]
                    [?t :tweet/text ?text]
                    [?t :tweet/likes ?likes]
                    [?t :tweet/retweets ?retweets]
                    [?t :tweet/replies ?replies]
                    [?t :tweet/user ?u]
                    [?u :user/id ?user-id]]])

(def user-rules '[[(get-user-rule ?id ?active ?name ?email ?username)
                   [?t :user/id ?id]
                   [?t :user/active ?active]
                   [?t :user/name ?name]
                   [?t :user/email ?email]
                   [?t :user/username ?username]]])

(def like-rules '[[(get-like-rule ?id ?created-at ?user-id ?source-tweet-id)
                   [?l :like/id ?id]
                   [?l :like/created-at ?created-at]
                   [?l :like/user ?u]
                   [?u :user/id ?user-id]
                   [?l :like/source-tweet ?s]
                   [?s :tweet/id ?source-tweet-id]]])

;; TODO: We can probably reuse tweet-rules.
(def reply-rules '[[(get-reply-rule ?id ?user-id ?text ?created-at ?likes ?retweets ?replies ?source-tweet-id)
                    [?r :tweet/id ?id]
                    [?r :tweet/created-at ?created-at]
                    [?r :tweet/text ?text]
                    [?r :tweet/likes ?likes]
                    [?r :tweet/retweets ?retweets]
                    [?r :tweet/replies ?replies]
                    [?r :tweet/user ?u]
                    [?u :user/id ?user-id]
                    [?r :reply/source-tweet ?s]
                    [?s :tweet/id ?source-tweet-id]]])

(def fetch-tweets-q
  "[:find ?id ?user-id ?text ?created-at ?likes ?retweets ?replies
    :in $ % <params>
    :where
    (get-tweet-rule ?id ?user-id ?text ?created-at ?likes ?retweets ?replies)])")

(def fetch-users-q
  "[:find ?id ?active ?name ?email ?username
    :in $ % <params>
    :where
    (get-user-rule ?id ?active ?name ?email ?username)]")

(def fetch-likes-q
  "[:find ?id ?created-at ?user-id ?source-tweet-id
    :in $ % <params>
    :where
    (get-like-rule ?id ?created-at ?user-id ?source-tweet-id)]")

(def fetch-replies-q
  "[:find ?id ?user-id ?text ?created-at ?likes ?retweets ?replies
    :in $ % <params>
    :where
    (get-reply-rule ?id ?user-id ?text ?created-at ?likes ?retweets ?replies ?source-tweet-id)])")

(defn v
  "Transforms a keyword k into a Datomic query variable symbol, e.g.,
  (v :user-id) => ?user-id"
  [k]
  (symbol (str "?" (name k))))

(defn make-query
  "Makes a query by replacing the token '<params>' in q with a list of keyword params."
  [q params]
  (read-string (clojure.string/replace q #"<params>" (clojure.string/join " " (map v params)))))

(defn map-if
  "Applies f to each value of m if p, a function of key and value, is truthy."
  [m p f]
  (into {} (map (fn [[k v]] (if (p k v) [k (f v)] [k v])) m)))

(defn map-uuid
  "Converts each value of m into an UUID if the respective key is in the set ks."
  [m ks]
  (map-if m (fn [k _v] (k ks)) (fn [v] (UUID/fromString v))))

(defn inst->ZonedDateTime
  "Converts from java.time.Instant to java.time.ZonedDateTime."
  [inst]
  (println inst)
  (ZonedDateTime/ofInstant (.toInstant inst) (ZoneId/systemDefault)))

(defn ZonedDateTime->inst
  "Converts from java.time.ZonedDateTime to java.util.Date (#inst)"
  [zdt]
  (Date/from (.toInstant zdt)))

(defn make-tweet
  [{:keys [id user-id text publish-date likes retweets replies]}]
  (let [uuid (UUID/fromString id)
        created-at (ZonedDateTime->inst publish-date)
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

(defn make-like
  [{:keys [id created-at user-id source-tweet-id]}]
  (let [uuid (UUID/fromString id)
        created-at (ZonedDateTime->inst created-at)
        user-uuid (UUID/fromString user-id)
        source-tweet-uuid (UUID/fromString source-tweet-id)]
    #:like{:id           uuid
           :created-at   created-at
           :user         [:user/id user-uuid]
           :source-tweet [:tweet/id source-tweet-uuid]}))

(defn make-reply
  [source-tweet-id reply]
  (let [source-tweet-uuid (UUID/fromString source-tweet-id)]
    (-> (make-tweet reply)
        (assoc :reply/source-tweet [:tweet/id source-tweet-uuid]))))

(defn update-tweet!
  [conn tweet]
  (do-transaction conn [(make-tweet tweet)]))

(defn update-user!
  [conn user]
  (do-transaction conn [(make-user user)]))

(defn update-like!
  [conn like]
  (do-transaction conn [(make-like like)]))

(defn update-reply!
  [conn source-tweet-id reply]
  (do-transaction conn [(make-reply source-tweet-id reply)]))

(defn fetch-tweets!
  [repo criteria]
  (let [conn (:conn repo)
        db (d/db conn)]
    (let [mc (map-uuid criteria #{:id :user-id})
          params (keys mc)
          params-val (vals mc)]
      (as-> (apply (partial d/q
                            (make-query fetch-tweets-q params)
                            db
                            tweet-rules)
                   params-val) results
            (map (fn [result] (apply core/->Tweet result)) results)
            (map (fn [result] (update result :publish-date inst->ZonedDateTime)) results)
            (map (fn [result] (update result :id str)) results)
            (map (fn [result] (update result :user-id str)) results)))))

(defn fetch-users!
  [repo criteria]
  (let [conn (:conn repo)
        db (d/db conn)]
    (let [mc (map-uuid criteria #{:id})
          params (keys mc)
          params-val (vals mc)]
      (as-> (apply (partial d/q
                            (make-query fetch-users-q params)
                            db
                            user-rules)
                   params-val) results
            (map (fn [result] (apply core/->User result)) results)
            (map (fn [result] (update result :id str)) results)))))

(defn fetch-likes!
  [repo criteria]
  (let [conn (:conn repo)
        db (d/db conn)]
    (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
          params (keys mc)
          params-val (vals mc)]
      (as-> (apply (partial d/q
                            (make-query fetch-likes-q params)
                            db
                            like-rules)
                   params-val) results
            (map (fn [result] (apply core/->TweetLike result)) results)
            (map (fn [result] (update result :id str)) results)
            (map (fn [result] (update result :user-id str)) results)
            (map (fn [result] (update result :source-tweet-id str)) results)))))

(defn fetch-replies!
  [repo criteria]
  (let [conn (:conn repo)
        db (d/db conn)]
    (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
          params (keys mc)
          params-val (vals mc)]
      (as-> (apply (partial d/q
                            (make-query fetch-replies-q params)
                            db
                            reply-rules)
                   params-val) results
            (do (println results) results)
            (map (fn [result] (apply core/->Tweet result)) results)
            (map (fn [result] (update result :publish-date inst->ZonedDateTime)) results)
            (map (fn [result] (update result :id str)) results)
            (map (fn [result] (update result :user-id str)) results)))))