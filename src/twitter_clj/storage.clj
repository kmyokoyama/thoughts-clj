(ns twitter-clj.storage)

(defprotocol Storage
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-thread! [this thread])
  (fetch-users! [this])
  (fetch-tweets! [this])
  (fetch-tweets-by-user! [this user-id])
  (fetch-threads! [this])
  (fetch-thread-by-id! [this thread-id]))
