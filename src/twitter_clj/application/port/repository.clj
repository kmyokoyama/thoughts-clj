(ns twitter-clj.application.port.repository)

(defprotocol Repository
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-like! [this like])
  (update-replies! [this source-tweet-id reply])
  (update-retweets! [this retweet])

  (fetch-users! [this key criteria])
  (fetch-tweets! [this key criteria])
  (fetch-likes! [this key criteria])
  (fetch-replies! [this key criteria])
  (fetch-retweets! [this key criteria])

  (remove-like! [this user-id tweet-id])
  (find-users! [this criteria]))
