(ns twitter-clj.adapter.cache.in-mem
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.port.cache :as cache])
  (:import [java.time ZonedDateTime]))

(defn- shutdown
  [cache]
  (reset! (:sessions cache) {})
  (reset! (:feeds cache) {})
  cache)

(defn- session-ids-by-user-id
  [sessions user-id]
  (->> sessions
       (vals)
       (filter (fn [session] (= user-id (:user-id session))))
       (map :id)))

(defrecord InMemoryCache [sessions feeds]
  component/Lifecycle
  (start
    [cache]
    (log/info "Starting in-memory cache")
    cache)

  (stop
    [cache]
    (log/info "Stopping in-memory cache")
    (shutdown cache))

  cache/Cache
  (update-session! [_ session]
    (swap! sessions assoc (:id session) session))

  (update-feed!
    [_ user-id feed ttl]
    (let [expiration (.plusSeconds (ZonedDateTime/now) ttl)]
      (swap! feeds assoc user-id {:feed feed :expiration expiration})
      feed))

  (fetch-feed!
    [_ user-id limit offset]
    (let [user-feed (get @feeds user-id)]
      (if (nil? user-feed)
        []
        (if (.isBefore (:expiration user-feed) (ZonedDateTime/now))
          (swap! feeds dissoc user-id)
          (->> user-feed :feed (drop offset) (take limit))))))

  (remove-session! [_ criteria]
    (case (key (first criteria))
      :session-id (swap! sessions dissoc (val (first criteria)))
      :user-id (swap! sessions (fn [sessions-map]
                                 (let [user-id (val (first criteria))
                                       session-ids (session-ids-by-user-id sessions-map user-id)]
                                   (doseq [session-id session-ids]
                                     (dissoc sessions-map session-id))))))))

(defn make-in-mem-cache
  []
  (map->InMemoryCache {:sessions (atom {})
                       :feeds (atom {})}))