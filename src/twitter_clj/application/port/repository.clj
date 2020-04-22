(ns twitter-clj.application.port.repository)

(defprotocol Repository
  (update-password! [this user-id password])
  (update-user! [this user])
  (update-tweet! [this tweet])
  (update-like! [this like])
  (update-reply! [this source-tweet-id reply])
  (update-retweet! [this retweet])
  (update-session! [this session])

  (fetch-password! [this user-id])
  (fetch-users! [this criteria])
  (fetch-tweets! [this criteria])
  (fetch-likes! [this criteria])
  (fetch-replies! [this criteria])
  (fetch-retweets! [this criteria])
  (fetch-sessions! [this criteria])

  (remove-like! [this criteria])
  (remove-session! [this criteria]))