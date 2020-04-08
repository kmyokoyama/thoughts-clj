(ns twitter-clj.adapter.rest.config
  (:require [buddy.auth.backends :as backends]
            [twitter-clj.application.config :refer [system-config]]
            [twitter-clj.adapter.rest.util :refer [add-leading-slash join-path]]))

(def rest-config (get-in system-config [:http :api]))

(def jws-backend (backends/jws {:secret (:jws-secret rest-config)
                                :token-name "Bearer"
                                :options {:alg :hs512}}))

(defn -path-prefix
  [config path]
  (->> (list (:path-prefix config)
             (:version config)
             path)
      (apply join-path)
      (add-leading-slash)))

(def path-prefix (partial -path-prefix rest-config))

(def routes-map {:get-tweet-by-id (path-prefix "/tweet/:tweet-id")
                 :get-tweets-by-user-id (path-prefix "/user/:user-id/tweets")
                 :get-user-by-id (path-prefix "/user/:user-id")
                 :get-replies-by-tweet-id (path-prefix "/tweet/:tweet-id/replies")
                 :get-retweets-by-tweet-id (path-prefix "/tweet/:tweet-id/retweets")
                 :get-retweet-by-id (path-prefix "/retweet/:retweet-id")
                 :add-tweet (path-prefix "/tweet")
                 :add-reply (path-prefix "/tweet/:tweet-id/reply")
                 :add-retweet (path-prefix "/tweet/:tweet-id/retweet")
                 :add-retweet-with-comment (path-prefix "/tweet/:tweet-id/retweet-comment")
                 :tweet-react (path-prefix "/tweet/:tweet-id/react")})