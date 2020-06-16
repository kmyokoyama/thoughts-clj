(ns twitter-clj.adapter.cache.redis
  (:require [com.stuartsierra.component :as component]
            [taoensso.carmine :as car :refer [wcar]]
            [taoensso.timbre :as log]
            [twitter-clj.application.port.cache :as cache]))

(defn- key-join
  [& key-parts]
  (clojure.string/join \: key-parts))

(defn- feeds-key
  [user-id]
  (key-join "feeds" user-id))

(defn- user-sessions-key
  [user-id]
  (key-join "users" user-id "sessions"))

(defn- session-key
  [session-id]
  (key-join "sessions" session-id))

(defn- vec->map
  [v]
  (apply array-map v))

(defn- keywordize
  [m]
  (zipmap (map keyword (keys m)) (vals m)))

(defmulti remove-session (fn [_ criteria] (key (first criteria))))

(defmethod remove-session :session-id
  [conn {session-id :session-id}]
  (let [s (wcar conn (car/hgetall (session-key session-id)))
        user-id (-> s vec->map keywordize :user-id)]
    (wcar conn
          (car/srem (user-sessions-key user-id) session-id)
          (car/del (session-key session-id)))))

(defmethod remove-session :user-id
  [conn {user-id :user-id}]
  (let [session-ids (wcar conn (car/smembers (user-sessions-key user-id)))]
    (wcar conn
          (car/del (user-sessions-key user-id))
          (apply car/del (map session-key session-ids)))))

(defrecord RedisCache [uri conn]
  component/Lifecycle
  (start
    [cache]
    (log/info "Starting Redis cache")
    (let [redis-conn {:pool {} :spec {:uri uri}}]
      (wcar redis-conn (car/ping))                          ;; Throws a ConnectException preventing the component from starting.
      (assoc cache :conn redis-conn)))

  (stop
    [cache]
    (log/info "Stopping Redis cache"))

  cache/Cache
  (update-session!
    [_ session]
    (let [user-id (:user-id session)
          session-id (:id session)]
      (wcar conn
            (car/sadd (user-sessions-key user-id) session-id)
            (apply car/hmset (session-key session-id) (reduce into [] session)))))

  (update-feed!
    [_ user-id feed ttl]
    (let [user-key (feeds-key user-id)]
      (wcar conn (apply car/lpush user-key (reverse feed)))
      (wcar conn (car/expire user-key ttl))
      feed))

  (fetch-feed!
    [_ user-id limit offset]
    (let [user-key (feeds-key user-id)
          stop (+ offset limit -1)]
      (wcar conn (car/lrange user-key offset stop))))

  (remove-session!
    [_ criteria]
    (remove-session conn criteria)))

(defn make-redis-cache
  [uri]
  (map->RedisCache {:uri uri}))