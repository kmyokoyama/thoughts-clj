(ns twitter-clj.application.port.repository)

(defprotocol Repository
  (update-password! [this user-id password])
  (update-user! [this user])
  (update-tweet! [this tweet hashtags])
  (update-like! [this like])
  (update-reply! [this source-tweet-id reply hashtags])
  (update-retweet! [this retweet hashtags])
  (update-follow! [this follower followed])

  (fetch-password! [this user-id])
  (fetch-users! [this criteria])
  (fetch-tweets! [this criteria])
  (fetch-likes! [this criteria])
  (fetch-replies! [this criteria])
  (fetch-retweets! [this criteria])
  (fetch-following! [this follower-id])
  (fetch-followers! [this followed-id])

  (remove-like! [this criteria])
  (remove-follow! [this follower-id followed-id]))