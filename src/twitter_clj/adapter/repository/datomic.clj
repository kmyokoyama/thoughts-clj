(ns twitter-clj.adapter.repository.datomic
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.core :refer :all]
            [twitter-clj.application.port.repository :as repository]
            [clojure.java.io :as io])
  (:import [java.util Date UUID]
           [java.time ZonedDateTime ZoneId]))

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

(defn query
  [db find in where rules params]
  (let [q (vec (concat [:find] find
                       [:in] in
                       [:where] where))]
    (apply (partial d/q
                    q
                    db
                    rules)
           params)))

(defn- map-if
  "Applies f to each value of m if p, a function of key and value, is truthy."
  [m p f]
  (into {} (map (fn [[k v]] (if (p k v) [k (f v)] [k v])) m)))

(defn- map-uuid
  "Converts each value of m into an UUID if the respective key is in the set ks."
  [m ks]
  (map-if m (fn [k _v] (k ks)) (fn [v] (UUID/fromString v))))

(defn- inst->ZonedDateTime
  "Converts from java.time.Instant to java.time.ZonedDateTime."
  [inst]
  (ZonedDateTime/ofInstant (.toInstant inst) (ZoneId/systemDefault)))

(defn- ZonedDateTime->inst
  "Converts from java.time.ZonedDateTime to java.util.Date (#inst)"
  [zdt]
  (Date/from (.toInstant zdt)))

(defn- v
  "Transforms a keyword k into a Datomic query variable symbol, e.g.,
  (v :user-id) => ?user-id"
  [k]
  (symbol (str "?" (name k))))

(def ^:private tweet-rules '[[(get-tweet-rule ?id ?user-id ?text ?created-at ?likes ?retweets ?replies)
                              [?t :tweet/id ?id]
                              [?t :tweet/created-at ?created-at]
                              [?t :tweet/text ?text]
                              [?t :tweet/likes ?likes]
                              [?t :tweet/retweets ?retweets]
                              [?t :tweet/replies ?replies]
                              [?t :tweet/user ?u]
                              [?u :user/id ?user-id]]])

(def ^:private user-rules '[[(get-user-rule ?id ?active ?name ?email ?username ?following ?followers)
                             [?t :user/id ?id]
                             [?t :user/active ?active]
                             [?t :user/name ?name]
                             [?t :user/email ?email]
                             [?t :user/username ?username]
                             [?t :user/following ?following]
                             [?t :user/followers ?followers]]])

(def ^:private following-rules '[[(get-following-rule ?follower-id ?followed-id ?active ?name ?email ?username ?following ?followers)
                                  [?r :user/id ?follower-id]
                                  [?r :user/follow ?d]
                                  [?d :user/id ?followed-id]
                                  [?d :user/active ?active]
                                  [?d :user/name ?name]
                                  [?d :user/email ?email]
                                  [?d :user/username ?username]
                                  [?d :user/following ?following]
                                  [?d :user/followers ?followers]]])

(def ^:private followers-rules '[[(get-followers-rule ?followed-id ?follower-id ?active ?name ?email ?username ?following ?followers)
                                  [?d :user/id ?followed-id]
                                  [?r :user/follow ?d]
                                  [?r :user/id ?follower-id]
                                  [?r :user/active ?active]
                                  [?r :user/name ?name]
                                  [?r :user/email ?email]
                                  [?r :user/username ?username]
                                  [?r :user/following ?following]
                                  [?r :user/followers ?followers]]])

(def ^:private like-rules '[[(get-like-rule ?id ?created-at ?user-id ?source-tweet-id)
                             [?l :like/id ?id]
                             [?l :like/created-at ?created-at]
                             [?l :like/user ?u]
                             [?u :user/id ?user-id]
                             [?l :like/source-tweet ?s]
                             [?s :tweet/id ?source-tweet-id]]])

;; TODO: We can probably reuse tweet-rules.
(def ^:private reply-rules '[[(get-reply-rule ?id ?user-id ?text ?created-at ?likes ?retweets ?replies ?source-tweet-id)
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

(def ^:private retweet-rules '[[(get-retweet-rule ?id ?user-id ?has-comment ?comment ?created-at ?source-tweet-id)
                                [?rt :retweet/id ?id]
                                [?rt :retweet/created-at ?created-at]
                                [?rt :retweet/has-comment ?has-comment]
                                [?rt :retweet/comment ?comment]
                                [?rt :retweet/user ?u]
                                [?u :user/id ?user-id]
                                [?rt :retweet/source-tweet ?s]
                                [?s :tweet/id ?source-tweet-id]]])

(defn- make-tweet
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

(defn- make-user
  [{:keys [id active name email username following followers]}]
  (let [uuid (UUID/fromString id)]
    #:user{:id        uuid
           :active    active
           :name      name
           :email     email
           :username  username
           :following following
           :followers followers}))

(defn- make-like
  [{:keys [id created-at user-id source-tweet-id]}]
  (let [uuid (UUID/fromString id)
        created-at (ZonedDateTime->inst created-at)
        user-uuid (UUID/fromString user-id)
        source-tweet-uuid (UUID/fromString source-tweet-id)]
    #:like{:id           uuid
           :created-at   created-at
           :user         [:user/id user-uuid]
           :source-tweet [:tweet/id source-tweet-uuid]}))

(defn- make-reply
  [source-tweet-id reply]
  (let [source-tweet-uuid (UUID/fromString source-tweet-id)]
    (-> (make-tweet reply)
        (assoc :reply/source-tweet [:tweet/id source-tweet-uuid]))))

(defn- make-retweet
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

(defn- make-password
  [user-id password]
  (let [user-uuid (UUID/fromString user-id)]
    {:db/id         [:user/id user-uuid]
     :user/password password}))

(defn- make-session
  [{:keys [:id :user-id :created-at]}]
  (let [session-uuid (UUID/fromString id)
        user-uuid (UUID/fromString user-id)
        created-at-inst (ZonedDateTime->inst created-at)]
    {:session/id         session-uuid
     :session/user       [:user/id user-uuid]
     :session/created-at created-at-inst}))

(defn make-follow
  [follower-id followed-id]
  (let [follower-uuid (UUID/fromString follower-id)
        followed-uuid (UUID/fromString followed-id)]
    {:db/id       [:user/id follower-uuid]
     :user/follow #{[:user/id followed-uuid]}}))

(defrecord DatomicRepository [uri conn]
  component/Lifecycle
  (start
    [this]
    (log/info "Starting Datomic repository")
    (if (d/create-database uri)
      (log/info "Creating database")
      (log/info "Database already exists"))
    (let [conn (d/connect uri)]
      (if (clojure.string/starts-with? uri "datomic:mem")
        (do
          (log/info "Loading schema into database")
          (load-schema conn "schema.edn")))
      (assoc this :conn conn)))

  (stop
    [_this]
    (log/info "Stopping Datomic repository"))

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

  (update-follow!
    [_ follower followed]
    (let [follower-id (:id follower)
          followed-id (:id followed)]
      (do-transaction conn [(make-follow follower-id followed-id)])))

  (fetch-tweets!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?user-id ?text ?created-at ?likes ?retweets ?replies)
                    (concat '($ %) (map v params))
                    '((get-tweet-rule ?id ?user-id ?text ?created-at ?likes ?retweets ?replies))
                    tweet-rules
                    params-val)
             (map (fn [result] (apply ->Tweet result)))
             (map (fn [result] (update result :publish-date inst->ZonedDateTime)))
             (map (fn [result] (update result :id str)))
             (map (fn [result] (update result :user-id str)))))))

  (fetch-users!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?active ?name ?email ?username ?following ?followers)
                    (concat '($ %) (map v params))
                    '((get-user-rule ?id ?active ?name ?email ?username ?following ?followers))
                    user-rules
                    params-val)
             (map (fn [result] (apply ->User result)))
             (map (fn [result] (update result :id str)))))))

  (fetch-following!
    [_ follower-id]
    (let [db (d/db conn)]
      (let [follower-uuid (UUID/fromString follower-id)]
        (->> (d/q '[:find ?followed-id ?active ?name ?email ?username ?following ?followers
                    :in $ % ?follower-id
                    :where (get-following-rule ?follower-id ?followed-id ?active ?name ?email ?username ?following ?followers)]
                  db
                  following-rules
                  follower-uuid)
             (map (fn [result] (apply ->User result)))
             (map (fn [result] (update result :id str)))))))

  (fetch-followers!
    [_ followed-id]
    (let [db (d/db conn)]
      (let [followed-uuid (UUID/fromString followed-id)]
        (->> (d/q '[:find ?follower-id ?active ?name ?email ?username ?following ?followers
                    :in $ % ?followed-id
                    :where (get-followers-rule ?followed-id ?follower-id ?active ?name ?email ?username ?following ?followers)]
                  db
                  followers-rules
                  followed-uuid)
             (map (fn [result] (apply ->User result)))
             (map (fn [result] (update result :id str)))))))

  (fetch-likes!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?created-at ?user-id ?source-tweet-id)
                    (concat '($ %) (map v params))
                    '((get-like-rule ?id ?created-at ?user-id ?source-tweet-id))
                    like-rules
                    params-val)
             (map (fn [result] (apply ->TweetLike result)))
             (map (fn [result] (update result :id str)))
             (map (fn [result] (update result :user-id str)))
             (map (fn [result] (update result :source-tweet-id str)))))))

  (fetch-replies!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?user-id ?text ?created-at ?likes ?retweets ?replies)
                    (concat '($ %) (map v params))
                    '((get-reply-rule ?id ?user-id ?text ?created-at ?likes ?retweets ?replies ?source-tweet-id))
                    reply-rules
                    params-val)
             (map (fn [result] (apply ->Tweet result)))
             (map (fn [result] (update result :publish-date inst->ZonedDateTime)))
             (map (fn [result] (update result :id str)))
             (map (fn [result] (update result :user-id str)))))))

  (fetch-retweets!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?user-id ?has-comment ?comment ?created-at ?source-tweet-id)
                    (concat '($ %) (map v params))
                    '((get-retweet-rule ?id ?user-id ?has-comment ?comment ?created-at ?source-tweet-id))
                    retweet-rules
                    params-val)
             (map (fn [result] (apply ->Retweet result)))
             (map (fn [result] (update result :id str)))
             (map (fn [result] (update result :user-id str)))
             (map (fn [result] (update result :source-tweet-id str)))))))

  (fetch-password!
    [_ user-id]
    (let [db (d/db conn)]
      (let [user-uuid (UUID/fromString user-id)]
        (ffirst (d/q '[:find ?password
                       :in $ % ?user-id
                       :where
                       [?u :user/id ?user-id]
                       [?u :user/password ?password]]
                     db
                     retweet-rules
                     user-uuid)))))

  (fetch-sessions!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-tweet-id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?user-id ?created-at)
                    (concat '($ %) (map v params))
                    '([?s :session/user ?u]
                      [?u :user/id ?user-id]
                      [?s :session/id ?id]
                      [?s :session/created-at ?created-at])
                    retweet-rules
                    params-val)
             (map (fn [result] (apply ->Session result)))
             (map (fn [result] (update result :id str)))
             (map (fn [result] (update result :user-id str)))
             (map (fn [result] (update result :created-at inst->ZonedDateTime)))))))

  (remove-like!
    [this criteria]
    (->> (repository/fetch-likes! this criteria)
         (map :id)
         (map (fn [id] (UUID/fromString id)))
         (map (fn [uuid] [:db/retractEntity [:like/id uuid]]))
         (do-transaction conn)))

  (remove-follow!
    [_ follower followed]
    (let [follower-uuid (UUID/fromString (:id follower))
          followed-uuid (UUID/fromString (:id followed))]
      (do-transaction conn [[:db/retract [:user/id follower-uuid] :user/follow [:user/id followed-uuid]]])))

  (remove-session!
    [this criteria]
    (->> (repository/fetch-sessions! this criteria)
         (map :id)
         (map (fn [id] (UUID/fromString id)))
         (map (fn [uuid] [:db/retractEntity [:session/id uuid]]))
         (do-transaction conn))))

(defn make-datomic-repository
  [uri]
  (map->DatomicRepository {:uri uri}))