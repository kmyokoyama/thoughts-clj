(ns thoughts.application.port.protocol.repository)

(defprotocol UserRepository
  (update-user! [this user])
  (fetch-users! [this criteria])

  (update-password! [this user-id password])
  (fetch-password! [this user-id])

  (update-follow! [this follower followed])
  (fetch-following! [this follower-id])
  (fetch-followers! [this followed-id])
  (remove-follow! [this follower-id followed-id]))

(defprotocol ThoughtRepository
  (update-thought! [this thought hashtags])
  (fetch-thoughts! [this criteria])

  (update-like! [this like])
  (fetch-likes! [this criteria])
  (remove-like! [this criteria])

  (update-reply! [this source-thought-id reply hashtags])
  (fetch-replies! [this criteria])

  (update-rethought! [this rethought hashtags])
  (fetch-rethoughts! [this criteria]))