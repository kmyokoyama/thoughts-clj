(ns twitter-clj.storage)

(defprotocol Storage
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-thread! [this thread])
  (fetch-thread-by-id! [this thread-id])
  (inspect-users! [this])
  (inspect-tweets! [this])
  (inspect-threads! [this]))