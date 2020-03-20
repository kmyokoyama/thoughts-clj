(ns twitter-clj.application.port.storage)

(defprotocol Storage
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-like! [this like])
  (fetch-user-by-id! [this user-id])
  (fetch-tweets-by-user! [this user-id])
  (fetch-tweet-by-id! [this tweet-id])
  (remove-like! [this user-id tweet-id])
  (find-users! [this criteria])
  (find-like! [this user-id tweet-id]))
