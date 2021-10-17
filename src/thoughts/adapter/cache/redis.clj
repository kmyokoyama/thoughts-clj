(ns thoughts.adapter.cache.redis
  (:require [com.stuartsierra.component :as component]
            [taoensso.carmine :as carmine]
            [taoensso.timbre :as log]
            [thoughts.port.cache :as p]
            [thoughts.port.config :as p.config]))

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
  (let [s (carmine/wcar conn (carmine/hgetall (session-key session-id)))
        user-id (-> s vec->map keywordize :user-id)]
    (carmine/wcar conn
                  (carmine/srem (user-sessions-key user-id) session-id)
                  (carmine/del (session-key session-id)))))

(defmethod remove-session :user-id
  [conn {user-id :user-id}]
  (let [session-ids (carmine/wcar conn (carmine/smembers (user-sessions-key user-id)))]
    (carmine/wcar conn
                  (carmine/del (user-sessions-key user-id))
                  (apply carmine/del (map session-key session-ids)))))

(defrecord RedisCache [conn config]
  component/Lifecycle
  (start
    [cache]
    (log/info "Starting Redis cache")
    (let [uri (p.config/value-of! config :redis-uri)
          redis-conn {:pool {} :spec {:uri uri}}]
      (carmine/wcar redis-conn (carmine/ping))                          ;; Throws a ConnectException preventing the component from starting.
      (assoc cache :conn redis-conn)))

  (stop
    [cache]
    (log/info "Stopping Redis cache"))

  p/Cache
  (update-session!
    [_ session]
    (let [user-id (:user-id session)
          session-id (:id session)]
      (carmine/wcar conn
                    (carmine/sadd (user-sessions-key user-id) session-id)
                    (apply carmine/hmset (session-key session-id) (reduce into [] session)))))

  (update-feed!
    [_ user-id feed ttl]
    (let [user-key (feeds-key user-id)]
      (carmine/wcar conn (apply carmine/lpush user-key (reverse feed)))
      (carmine/wcar conn (carmine/expire user-key ttl))
      feed))

  (fetch-feed!
    [_ user-id limit offset]
    (let [user-key (feeds-key user-id)
          stop (+ offset limit -1)]
      (carmine/wcar conn (carmine/lrange user-key offset stop))))

  (remove-session!
    [_ criteria]
    (remove-session conn criteria)))

(defn make-redis-cache
  []
  (map->RedisCache {}))