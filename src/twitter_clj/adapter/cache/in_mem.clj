(ns twitter-clj.adapter.cache.in-mem
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [twitter-clj.application.port.cache :as cache])
  (:import [java.time ZonedDateTime]))

(defn- shutdown
  [cache]
  (reset! (:feeds cache) {})
  cache)

(defrecord InMemoryCache [feeds]
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
          (->> user-feed :feed (drop offset) (take limit)))))))

(defn make-in-mem-cache
  []
  (map->InMemoryCache {:feeds (atom {})}))