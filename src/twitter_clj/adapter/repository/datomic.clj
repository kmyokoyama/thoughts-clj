(ns twitter-clj.adapter.repository.datomic
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :refer :all]
            [twitter-clj.application.port.repository :as repository]
            [clojure.java.io :as io])
  (:import [java.util Date UUID]
           [java.time ZonedDateTime ZoneId]))

(defn create-database
  [uri]
  (d/create-database uri))

(defn delete-database
  [uri]
  (d/delete-database uri))

(defn load-schema
  [conn resource]
  (let [m (-> resource io/resource slurp read-string)]
    (doseq [v (vals m)]
      (doseq [tx v]
        (d/transact conn tx)))))

(defn do-transaction
  [conn tx]
  (d/transact conn tx))

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

(def retweet-rules '[[(get-retweet-rule ?id ?user-id ?has-comment ?comment ?created-at ?source-tweet-id)
                      [?rt :retweet/id ?id]
                      [?rt :retweet/created-at ?created-at]
                      [?rt :retweet/has-comment ?has-comment]
                      [?rt :retweet/comment ?comment]
                      [?rt :retweet/user ?u]
                      [?u :user/id ?user-id]
                      [?rt :retweet/source-tweet ?s]
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

(def fetch-retweets-q
  "[:find ?id ?user-id ?has-comment ?comment ?created-at ?source-tweet-id
    :in $ % <params>
    :where
    (get-retweet-rule ?id ?user-id ?has-comment ?comment ?created-at ?source-tweet-id)]")

(def fetch-password-q
  "[:find ?password
    :in $ % ?user-id
    :where
    [?u :user/id ?user-id]
    [?u :user/password ?password]]")

(def fetch-session-q
  "[:find ?id ?user-id ?created-at
    :in $ % <params>
    :where
    [?s :session/user ?u]
    [?u :user/id ?user-id]
    [?s :session/id ?id]
    [?s :session/created-at ?created-at]]")

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

(defn make-retweet
  [{:keys [id user-id has-comment comment publish-date source-tweet-id]}]
  (let [uuid (UUID/fromString id)
        created-at (ZonedDateTime->inst publish-date)
        user-uuid (UUID/fromString user-id)
        source-tweet-uuid (UUID/fromString source-tweet-id)]
    #:retweet{:id           uuid
              :user         [:user/id user-uuid]
              :has-comment  has-comment
              :comment      (if-not has-comment "" comment) ;; TODO: Fix it. Perhaps we need to rethink the retweet model.
              :created-at   created-at
              :source-tweet [:tweet/id source-tweet-uuid]}))

(defn make-password
  [user-id password]
  (let [user-uuid (UUID/fromString user-id)]
    {:db/id         [:user/id user-uuid]
     :user/password password}))

(defn make-session
  [{:keys [:id :user-id :created-at]}]
  (let [session-uuid (UUID/fromString id)
        user-uuid (UUID/fromString user-id)
        created-at-inst (ZonedDateTime->inst created-at)]
    {:session/id         session-uuid
     :session/user       [:user/id user-uuid]
     :session/created-at created-at-inst}))

(defrecord DatomicStorage [uri conn]
  component/Lifecycle
  (start
    [this]
    (log/info "Starting Datomic storage")
    (let [connection (d/connect uri)]
      (assoc this :conn connection)))

  (stop
    [_this]
    (log/info "Stopping Datomic storage"))

  repository/Repository
  (update-tweet!
    [_ tweet]
    (do-transaction conn [(make-tweet tweet)])
    tweet)

  (update-user!
    [_ user]
    (do-transaction conn [(make-user user)])
    user)

  (update-like!
    [_ like]
    (do-transaction conn [(make-like like)])
    like)

  (update-reply!
    [_ source-tweet-id reply]
    (do-transaction conn [(make-reply source-tweet-id reply)])
    reply)

  (update-retweet!
    [_ retweet]
    (do-transaction conn [(make-retweet retweet)])
    retweet)

  (update-password!
    [_ user-id password]
    (do-transaction conn [(make-password user-id password)])
    user-id)

  (update-session!
    [_ session]
    (do-transaction conn [(make-session session)])
    session)

  (fetch-tweets!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id})
            params (keys mc)
            params-val (vals mc)]
        (as-> (apply (partial d/q
                              (make-query fetch-tweets-q params)
                              db
                              tweet-rules)
                     params-val) results
              (map (fn [result] (apply ->Tweet result)) results)
              (map (fn [result] (update result :publish-date inst->ZonedDateTime)) results)
              (map (fn [result] (update result :id str)) results)
              (map (fn [result] (update result :user-id str)) results)))))

  (fetch-users!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id})
            params (keys mc)
            params-val (vals mc)]
        (as-> (apply (partial d/q
                              (make-query fetch-users-q params)
                              db
                              user-rules)
                     params-val) results
              (map (fn [result] (apply ->User result)) results)
              (map (fn [result] (update result :id str)) results)))))

  (fetch-likes!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
            params (keys mc)
            params-val (vals mc)]
        (as-> (apply (partial d/q
                              (make-query fetch-likes-q params)
                              db
                              like-rules)
                     params-val) results
              (map (fn [result] (apply ->TweetLike result)) results)
              (map (fn [result] (update result :id str)) results)
              (map (fn [result] (update result :user-id str)) results)
              (map (fn [result] (update result :source-tweet-id str)) results)))))

  (fetch-replies!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
            params (keys mc)
            params-val (vals mc)]
        (as-> (apply (partial d/q
                              (make-query fetch-replies-q params)
                              db
                              reply-rules)
                     params-val) results
              (map (fn [result] (apply ->Tweet result)) results)
              (map (fn [result] (update result :publish-date inst->ZonedDateTime)) results)
              (map (fn [result] (update result :id str)) results)
              (map (fn [result] (update result :user-id str)) results)))))

  (fetch-retweets!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
            params (keys mc)
            params-val (vals mc)]
        (as-> (apply (partial d/q
                              (make-query fetch-retweets-q params)
                              db
                              retweet-rules)
                     params-val) results
              (map (fn [result] (apply ->Retweet result)) results)
              (map (fn [result] (update result :id str)) results)
              (map (fn [result] (update result :user-id str)) results)
              (map (fn [result] (update result :source-tweet-id str)) results)))))

  (fetch-password!
    [_ user-id]
    (let [db (d/db conn)]
      (let [user-uuid (UUID/fromString user-id)]
        (ffirst (d/q fetch-password-q db retweet-rules user-uuid)))))

  (fetch-sessions!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
            params (keys mc)
            params-val (vals mc)]
        (as-> (apply (partial d/q
                              (make-query fetch-session-q params)
                              db
                              retweet-rules)
                     params-val) results
              (map (fn [result] (apply ->Session result)) results)
              (map (fn [result] (update result :id str)) results)
              (map (fn [result] (update result :user-id str)) results)
              (map (fn [result] (update result :created-at inst->ZonedDateTime)) results)))))

  (remove-like!
    [this criteria]
    (->> (repository/fetch-likes! this criteria)
         (map :id)
         (map (fn [id] (UUID/fromString id)))
         (map (fn [uuid] [:db/retractEntity [:like/id uuid]]))
         (do-transaction conn)))

  (remove-session!
    [this criteria]
    (->> (repository/fetch-sessions! this criteria)
         (map :id)
         (map (fn [id] (UUID/fromString id)))
         (map (fn [uuid] [:db/retractEntity [:session/id uuid]]))
         (do-transaction conn))))

(defn make-datomic-storage
  [uri]
  (map->DatomicStorage {:uri uri}))