(ns thoughts.application.port.cache
  (:require [thoughts.application.port.protocol.cache :as p]))

(defn update-session!
  [cache session]
  (p/update-session! cache session))

(defn update-feed!
  [cache user-id feed ttl]
  (p/update-feed! cache user-id feed ttl))

(defn fetch-feed!
  [cache user-id limit offset]
  (p/fetch-feed! cache user-id limit offset))

(defn remove-session!
  [cache criteria]
  (p/remove-session! cache criteria))
