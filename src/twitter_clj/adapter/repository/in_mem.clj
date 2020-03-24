(ns twitter-clj.adapter.repository.in-mem
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.port.repository :as repository])
  (:import [java.util UUID]))

(declare shutdown)

(defn- to-uuid
  [str]
  (UUID/fromString str))

;; Driven-side.

(defrecord InMemoryStorage [users tweets replies retweets likes]
  component/Lifecycle
  (start [this]
    (log/info "Starting in-memory database")
    this)

  (stop [this]
    (log/info "Stopping in-memory database")
    (shutdown this))

  repository/Repository
  (update-user!
    [_ {user-id :id :as user}]
    (swap! users (fn [users] (assoc users user-id user)))
    user)

  (update-tweet!
    [_ {tweet-id :id :as tweet}]
    (swap! tweets (fn [tweets] (assoc tweets tweet-id tweet)))
    tweet)

  (update-replies!
    [_ source-tweet-id {reply-id :id :as reply}]
    (swap! replies (fn [replies] (update replies
                                         (to-uuid source-tweet-id)
                                         (fn [reply-ids] (conj (vec reply-ids) reply-id)))))
    reply)

  (update-retweets!
    [_ {retweet-id :id :as retweet}]
    (swap! retweets (fn [retweets] (assoc retweets retweet-id
                                                   (assoc retweet :tweet-id
                                                                  (get-in retweet [:tweet :id])))))
    retweet)

  (update-like!
    [_ {like-id :id source-tweet-id :tweet-id :as like}]
    (swap! likes (fn [likes] (update likes
                                     (to-uuid source-tweet-id)
                                     (fn [like-ids] (conj (vec like-ids) like-id)))))
    like)

  (fetch-tweets!
    [_ key criteria]
    (case criteria
      :by-id (get @tweets (to-uuid key))
      :by-user-id (filter #(= (:user-id %) key) (vals @tweets))))

  (fetch-retweets!
    [_ key criteria]
    (case criteria
      :by-id (if-let [retweet (get @retweets (to-uuid key))]
               (-> (assoc retweet :tweet (get @tweets (:tweet-id retweet)))
                   (dissoc retweet :tweet-id)))
      :by-source-tweet-id (->> (vals @retweets)
                               (filter (fn [retweet] (= (:tweet-id retweet) (to-uuid key))))
                               (map (fn [retweet] (-> (assoc retweet :tweet (get @tweets (to-uuid key)))
                                                      (dissoc retweet :tweet-id)))))))

  (fetch-replies!
    [_ key criteria]
    (case criteria
      :by-source-tweet-id (->> (get @replies (to-uuid key) [])
                               (map (fn [reply-id] (get @tweets (to-uuid reply-id)))))))

  (fetch-users!
    [_ key criteria]-
    (case criteria
      :by-id (get @users (to-uuid key))))

  (remove-like!
    [_ user-id tweet-id]
    (swap! likes (fn [likes] (update-in likes [tweet-id] dissoc user-id))))

  (find-users!
    [_ criteria]
    (filter (fn [user] (= criteria (select-keys user (keys criteria)))) (vals @users)))

  (find-like!
    [_ user-id tweet-id]
    (get-in @likes [tweet-id user-id])))

(defn make-in-mem-storage ;; Constructor.
  []
  (map->InMemoryStorage {:users    (atom {})
                         :tweets   (atom {})
                         :replies  (atom {})
                         :retweets (atom {})
                         :likes    (atom {})}))

(defn shutdown
  [repository]
  (reset! (:users repository) {})
  (reset! (:tweets repository) {})
  (reset! (:replies repository) {})
  (reset! (:retweets repository) {})
  (reset! (:likes repository) {})
  repository)