(ns twitter-clj.application.port.repository)

(defprotocol Repository
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-replies! [this source-tweet-id reply])
  (update-like! [this like])
  (update-retweets! [this retweet])

  (fetch-tweets! [this key criteria])
  (fetch-users! [this key criteria])
  (fetch-retweet-by-id! [this retweet-id])
  (fetch-retweets-by-source-tweet-id! [this source-tweet-id])
  (fetch-replies! [this key criteria])

  (remove-like! [this user-id tweet-id])
  (find-users! [this criteria])
  (find-like! [this user-id tweet-id]))
