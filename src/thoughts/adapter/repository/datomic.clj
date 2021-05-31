(ns thoughts.adapter.repository.datomic
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [thoughts.application.core :refer :all]
            [thoughts.application.port.repository :as repository]
            [thoughts.application.port.protocol.repository :as p]
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

(def ^:private thought-rules '[[(get-thought-rule ?id ?user-id ?text ?created-at ?likes ?rethoughts ?replies ?hashtag)]
                               [?t :thought/id ?id]
                               [?t :thought/created-at ?created-at]
                               [?t :thought/text ?text]
                               [?t :thought/likes ?likes]
                               [?t :thought/rethoughts ?rethoughts]
                               [?t :thought/replies ?replies]
                               [?t :thought/hashtags ?hashtag]
                               [?t :thought/user ?u]
                               [?u :user/id ?user-id]])

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

(def ^:private like-rules '[[(get-like-rule ?id ?created-at ?user-id ?source-thought-id)
                             [?l :like/id ?id]
                             [?l :like/created-at ?created-at]
                             [?l :like/user ?u]
                             [?u :user/id ?user-id]
                             [?l :like/source-thought ?s]
                             [?s :thought/id ?source-thought-id]]])

;; TODO: We can probably reuse thought-rules.
(def ^:private reply-rules '[[(get-reply-rule ?id ?user-id ?text ?created-at ?likes ?rethoughts ?replies ?source-thought-id)
                              [?r :thought/id ?id]
                              [?r :thought/created-at ?created-at]
                              [?r :thought/text ?text]
                              [?r :thought/likes ?likes]
                              [?r :thought/rethoughts ?rethoughts]
                              [?r :thought/replies ?replies]
                              [?r :thought/user ?u]
                              [?u :user/id ?user-id]
                              [?r :reply/source-thought ?s]
                              [?s :thought/id ?source-thought-id]]])

(def ^:private rethought-rules '[[(get-rethought-rule ?id ?user-id ?has-comment ?comment ?created-at ?source-thought-id)]
                                 [?rt :rethought/id ?id]
                                 [?rt :rethought/created-at ?created-at]
                                 [?rt :rethought/has-comment ?has-comment]
                                 [?rt :rethought/comment ?comment]
                                 [?rt :rethought/user ?u]
                                 [?u :user/id ?user-id]
                                 [?rt :rethought/source-thought ?s]
                                 [?s :thought/id ?source-thought-id]])

(defn- make-thought
  [{:keys [id user-id text publish-date likes rethoughts replies]} hashtags]
  (let [uuid (UUID/fromString id)
        created-at (ZonedDateTime->inst publish-date)
        user-uuid (UUID/fromString user-id)]
    #:thought{:id         uuid}
            :created-at created-at
            :text       text
            :likes      likes
            :rethoughts   rethoughts
            :replies    replies
            :user       [:user/id user-uuid]
            :hashtags   (set hashtags)))

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
  [{:keys [id created-at user-id source-thought-id]}]
  (let [uuid (UUID/fromString id)
        created-at (ZonedDateTime->inst created-at)
        user-uuid (UUID/fromString user-id)
        source-thought-uuid (UUID/fromString source-thought-id)]
    #:like{:id           uuid
           :created-at   created-at
           :user         [:user/id user-uuid]
           :source-thought [:thought/id source-thought-uuid]}))

(defn- make-reply
  [source-thought-id reply hashtags]
  (let [source-thought-uuid (UUID/fromString source-thought-id)]
    (-> (make-thought reply hashtags)
        (assoc :reply/source-thought [:thought/id source-thought-uuid]))))

(defn- make-rethought
  [{:keys [id user-id has-comment comment publish-date source-thought-id]} hashtags]
  (let [uuid (UUID/fromString id)
        created-at (ZonedDateTime->inst publish-date)
        user-uuid (UUID/fromString user-id)
        source-thought-uuid (UUID/fromString source-thought-id)]
    #:rethought{:id           uuid}
              :user         [:user/id user-uuid]
              :has-comment  has-comment
              :comment      (if has-comment comment "") ;; TODO: Fix it. Perhaps we need to rethink the rethought model.
              :created-at   created-at
              :source-thought [:thought/id source-thought-uuid]
              :hashtags     hashtags))

(defn- make-password
  [user-id password]
  (let [user-uuid (UUID/fromString user-id)]
    {:db/id         [:user/id user-uuid]
     :user/password password}))

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

  p/UserRepository
  (update-user!
    [_ user]
    (do-transaction conn [(make-user user)])
    user)

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

  (update-password!
    [_ user-id password]
    (do-transaction conn [(make-password user-id password)])
    user-id)

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
                     rethought-rules
                     user-uuid)))))

  (update-follow!
    [_ follower followed]
    (let [follower-id (:id follower)
          followed-id (:id followed)]
      (do-transaction conn [(make-follow follower-id followed-id)])))

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

  (remove-follow!
    [_ follower followed]
    (let [follower-uuid (UUID/fromString (:id follower))
          followed-uuid (UUID/fromString (:id followed))]
      (do-transaction conn [[:db/retract [:user/id follower-uuid] :user/follow [:user/id followed-uuid]]])))

  p/ThoughtRepository
  (update-thought!
    [_ thought hashtags]
    (do-transaction conn [(make-thought thought hashtags)])
    thought)

  (fetch-thoughts!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?user-id ?text ?created-at ?likes ?rethoughts ?replies)
                    (concat '($ %) (map v params))
                    '((get-thought-rule ?id ?user-id ?text ?created-at ?likes ?rethoughts ?replies ?hashtag))
                    thought-rules
                    params-val)
             (map (fn [result] (apply ->Thought result)))
             (map (fn [result] (update result :publish-date inst->ZonedDateTime)))
             (map (fn [result] (update result :id str)))
             (map (fn [result] (update result :user-id str)))))))

  (update-like!
    [_ like]
    (do-transaction conn [(make-like like)])
    like)

  (fetch-likes!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-thought-id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?created-at ?user-id ?source-thought-id)
                    (concat '($ %) (map v params))
                    '((get-like-rule ?id ?created-at ?user-id ?source-thought-id))
                    like-rules
                    params-val)
             (map (fn [result] (apply ->ThoughtLike result)))
             (map (fn [result] (update result :id str)))
             (map (fn [result] (update result :user-id str)))
             (map (fn [result] (update result :source-thought-id str)))))))

  (remove-like!
    [this criteria]
    (->> (repository/fetch-likes! this criteria)
         (map :id)
         (map (fn [id] (UUID/fromString id)))
         (map (fn [uuid] [:db/retractEntity [:like/id uuid]]))
         (do-transaction conn)))

  (update-reply!
    [_ source-thought-id reply hashtags]
    (do-transaction conn [(make-reply source-thought-id reply hashtags)])
    reply)

  (fetch-replies!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-thought-id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?user-id ?text ?created-at ?likes ?rethoughts ?replies)
                    (concat '($ %) (map v params))
                    '((get-reply-rule ?id ?user-id ?text ?created-at ?likes ?rethoughts ?replies ?source-thought-id))
                    reply-rules
                    params-val)
             (map (fn [result] (apply ->Thought result)))
             (map (fn [result] (update result :publish-date inst->ZonedDateTime)))
             (map (fn [result] (update result :id str)))
             (map (fn [result] (update result :user-id str)))))))

  (update-rethought!
    [_ rethought hashtags]
    (do-transaction conn [(make-rethought rethought hashtags)])
    rethought)

  (fetch-rethoughts!
    [_ criteria]
    (let [db (d/db conn)]
      (let [mc (map-uuid criteria #{:id :user-id :source-thought-id})
            params (keys mc)
            params-val (vals mc)]
        (->> (query db
                    '(?id ?user-id ?has-comment ?comment ?created-at ?source-thought-id)
                    (concat '($ %) (map v params))
                    '((get-rethought-rule ?id ?user-id ?has-comment ?comment ?created-at ?source-thought-id))
                    rethought-rules
                    params-val)
             (map (fn [result] (apply ->Rethought result)))
             (map (fn [result] (update result :id str)))
             (map (fn [result] (update result :user-id str)))
             (map (fn [result] (update result :source-thought-id str))))))))

(defn make-datomic-repository
  [uri]
  (map->DatomicRepository {:uri uri}))