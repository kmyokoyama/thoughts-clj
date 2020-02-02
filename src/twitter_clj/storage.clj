(ns twitter-clj.storage)

(defprotocol Storage
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-thread! [this thread])
  (fetch-thread-by-id! [this thread-id])
  (fetch-users! [this])
  (fetch-tweets! [this])
  (fetch-threads! [this]))