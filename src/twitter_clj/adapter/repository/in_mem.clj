(ns twitter-clj.adapter.repository.in-mem
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.port.repository :as repository]))

(defn- shutdown
  [repository]
  (reset! (:passwords repository) {})
  (reset! (:sessions repository) {})
  (reset! (:users repository) {})
  (reset! (:tweets repository) {})
  (reset! (:retweets repository) {})
  (reset! (:likes repository) {})
  (reset! (:join-tweet-likes repository) {})
  (reset! (:join-tweet-replies repository) {})
  (reset! (:join-tweet-retweets repository) {})
  (reset! (:following repository) {})
  repository)

;; Driven-side.

(defrecord InMemoryRepository [users tweets likes retweets
                               join-tweet-likes join-tweet-replies join-tweet-retweets
                               passwords sessions following hashtags]
  component/Lifecycle
  (start [this]
    (log/info "Starting in-memory database")
    this)

  (stop [this]
    (log/info "Stopping in-memory database")
    (shutdown this))

  repository/Repository
  (update-password!
    [_ user-id password]
    (swap! passwords assoc user-id password)
    user-id)

  ;(update-session!
  ;  [_ {session-id :id :as session}]
  ;  (swap! sessions assoc session-id session)
  ;  session)

  (update-user!
    [_ {user-id :id :as user}]
    (swap! users assoc user-id user)
    user)

  (update-tweet!
    [_ {tweet-id :id :as tweet} tags]
    (swap! tweets assoc tweet-id tweet)
    (doseq [tag tags]
      (swap! hashtags update tag (fn [tweet-ids] (conj (vec tweet-ids) tweet-id))))
    tweet)

  (update-like!
    [_ {like-id :id source-tweet-id :source-tweet-id :as like}]
    (swap! likes assoc like-id like)
    (swap! join-tweet-likes update source-tweet-id (fn [like-ids] (conj (vec like-ids) like-id)))
    like)

  (update-reply!
    [this source-tweet-id {reply-id :id :as reply} tags]
    (swap! join-tweet-replies update source-tweet-id (fn [reply-ids] (conj (vec reply-ids) reply-id)))
    (repository/update-tweet! this reply tags)
    reply)

  (update-retweet!
    [_ {retweet-id :id :as retweet} tags] ;; TODO: Consider hashtags for retweets.
    (swap! retweets assoc retweet-id retweet)
    (swap! join-tweet-retweets update (:source-tweet-id retweet) (fn [retweet-ids] (conj (vec retweet-ids) retweet-id)))
    retweet)

  (update-follow!
    [_ {follower-id :id} {followed-id :id}]
    (swap! following update follower-id (fn [following-ids] (conj (set following-ids) followed-id))))

  (fetch-password!
    [_ user-id]
    (get @passwords user-id))

  ;(fetch-sessions!
  ;  [_ criteria]
  ;  (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @sessions)))

  (fetch-users!
    [_ criteria]
    (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @users)))

  (fetch-tweets!
    [_ criteria]
    (if (= :hashtag (key (first criteria)))
      (->> (get @hashtags (val (first criteria)) [])
          (map (fn [tweet-id] (get @tweets tweet-id))))
      (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @tweets))))

  (fetch-likes!
    [_ criteria]
    (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @likes)))

  (fetch-replies!
    [_ criteria]
    (->> (get @join-tweet-replies (:source-tweet-id criteria) [])
         (map (fn [reply-id] (get @tweets reply-id)))))

  (fetch-retweets!
    [_ criteria]
    (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @retweets)))

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

  (remove-like!
    [_ criteria]
    (->> (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @likes))
         (map :id)
         (map (fn [like-id] (swap! likes dissoc like-id)))))

  (remove-follow!
    [_ {follower-id :id} {followed-id :id}]
    (swap! following update follower-id (fn [following-ids] (disj (set following-ids) followed-id)))))

;(remove-session!
;  [_ criteria]
;  (->> (filter (fn [e] (= criteria (select-keys e (keys criteria)))) (vals @sessions))
;       (map :id)
;       (map (fn [session-id] (swap! sessions dissoc session-id))))))

(defn make-in-mem-repository                                ;; Constructor.
  []
  (map->InMemoryRepository {:passwords           (atom {})
                            :sessions            (atom {})
                            :users               (atom {})
                            :tweets              (atom {})
                            :retweets            (atom {})
                            :likes               (atom {})
                            :join-tweet-likes    (atom {})
                            :join-tweet-replies  (atom {})
                            :join-tweet-retweets (atom {})
                            :following           (atom {})
                            :hashtags            (atom {})}))