(ns thoughts.adapter.repository.in-mem
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [thoughts.application.port.repository :as repository]
            [thoughts.application.port.protocol.repository :as p]))

(defn- shutdown
  [repository]
  (reset! (:passwords repository) {})
  (reset! (:sessions repository) {})
  (reset! (:users repository) {})
  (reset! (:thoughts repository) {})
  (reset! (:rethoughts repository) {})
  (reset! (:likes repository) {})
  (reset! (:join-thought-likes repository) {})
  (reset! (:join-thought-replies repository) {})
  (reset! (:join-thought-rethoughts repository) {})
  (reset! (:following repository) {})
  repository)

;; Driven-side.

(defrecord InMemoryRepository [users thoughts likes rethoughts
                               join-thought-likes join-thought-replies join-thought-rethoughts
                               passwords sessions following hashtags]
  component/Lifecycle
  (start [this]
    (log/info "Starting in-memory database")
    this)

  (stop [this]
    (log/info "Stopping in-memory database")
    (shutdown this))

  p/UserRepository
  (update-user!
    [_ {user-id :id :as user}]
    (swap! users assoc user-id user)
    user)

  (fetch-users!
    [_ criteria]
    (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @users)))

  (update-password!
    [_ user-id password]
    (swap! passwords assoc user-id password)
    user-id)

  (fetch-password!
    [_ user-id]
    (get @passwords user-id))

  (update-follow!
    [_ {follower-id :id} {followed-id :id}]
    (swap! following update follower-id (fn [following-ids] (conj (set following-ids) followed-id))))

  (fetch-following!
    [_ follower-id]
    (->> (get @following follower-id [])
         (map (fn [followed-id] (get @users followed-id)))))

  (fetch-followers!
    [_ followed-id]
    (->> (reduce-kv (fn [followers follower-id followed-ids]
                      (if (contains? followed-ids followed-id)
                        (conj followers follower-id)
                        followers))
                    [] @following)
         (map (fn [follower-id] (get @users follower-id)))))

  (remove-follow!
    [_ {follower-id :id} {followed-id :id}]
    (swap! following update follower-id (fn [following-ids] (disj (set following-ids) followed-id))))

  p/ThoughtRepository
  (update-thought!
    [_ {thought-id :id :as thought} tags]
    (swap! thoughts assoc thought-id thought)
    (doseq [tag tags]
      (swap! hashtags update tag (fn [thought-ids] (conj (vec thought-ids) thought-id))))
    thought)

  (fetch-thoughts!
    [_ criteria]
    (if (= :hashtag (key (first criteria)))
      (->> (get @hashtags (val (first criteria)) [])
           (map (fn [thought-id] (get @thoughts thought-id))))
      (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @thoughts))))

  (update-like!
    [_ {like-id :id source-thought-id :source-thought-id :as like}]
    (swap! likes assoc like-id like)
    (swap! join-thought-likes update source-thought-id (fn [like-ids] (conj (vec like-ids) like-id)))
    like)

  (fetch-likes!
    [_ criteria]
    (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @likes)))

  (remove-like!
    [_ criteria]
    (->> (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @likes))
         (map :id)
         (map (fn [like-id] (swap! likes dissoc like-id)))))

  (update-reply!
    [this source-thought-id {reply-id :id :as reply} tags]
    (swap! join-thought-replies update source-thought-id (fn [reply-ids] (conj (vec reply-ids) reply-id)))
    (repository/update-thought! this reply tags)
    reply)

  (fetch-replies!
    [_ criteria]
    (->> (get @join-thought-replies (:source-thought-id criteria) [])
         (map (fn [reply-id] (get @thoughts reply-id)))))

  (update-rethought!
    [_ {rethought-id :id :as rethought} tags]               ;; TODO: Consider hashtags for rethoughts.
    (swap! rethoughts assoc rethought-id rethought)
    (swap! join-thought-rethoughts update (:source-thought-id rethought) (fn [rethought-ids] (conj (vec rethought-ids) rethought-id)))
    rethought)

  (fetch-rethoughts!
    [_ criteria]
    (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @rethoughts))))

(defn make-in-mem-repository                                ;; Constructor.
  []
  (map->InMemoryRepository {:passwords               (atom {})
                            :sessions                (atom {})
                            :users                   (atom {})
                            :thoughts                (atom {})
                            :rethoughts              (atom {})
                            :likes                   (atom {})
                            :join-thought-likes      (atom {})
                            :join-thought-replies    (atom {})
                            :join-thought-rethoughts (atom {})
                            :following               (atom {})
                            :hashtags                (atom {})}))