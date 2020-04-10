(ns twitter-clj.application.port.repository)

(defprotocol Repository
  (update-password! [this user-id password])
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-like! [this like])
  (update-replies! [this source-tweet-id reply])
  (update-retweets! [this retweet])
  (update-sessions! [this user-id])

  (fetch-password! [this user-id])
  (fetch-users! [this key criteria])
  (fetch-tweets! [this key criteria])
  (fetch-likes! [this key criteria])
  (fetch-replies! [this key criteria])
  (fetch-retweets! [this key criteria])
  (fetch-session! [this user-id])

  (remove-like! [this key criteria])
  (remove-from-session! [this user-id]))