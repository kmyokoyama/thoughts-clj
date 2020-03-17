(ns twitter-clj.application.port.storage)

(defprotocol Storage
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-thread! [this thread])
  (fetch-user-by-id! [this user-id])
  (fetch-tweets-by-user! [this user-id])
  (fetch-tweet-by-id! [this tweet-id])
  (fetch-thread-by-id! [this thread-id])
  (new-user? [this email]))
