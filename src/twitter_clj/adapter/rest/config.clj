(ns twitter-clj.adapter.rest.config
  (:require [clojure.string :as string]
            [twitter-clj.application.config :refer [system-config]]
            [twitter-clj.adapter.rest.util :refer [join-path]]))

(def rest-config (get-in system-config [:http :api]))

(defn -path-prefix
  [config path]
  (->> (list (:version config)
             (:path-prefix config)
             path)
      (apply join-path)
      (str "/")))

(def path-prefix (partial -path-prefix rest-config))

(def routes-map {:get-tweet-by-id (path-prefix "/tweet/:tweet-id")
                 :get-user-by-id (path-prefix "/user/:user-id")
                 :get-replies-by-tweet-id (path-prefix "/tweet/:tweet-id/replies")
                 :get-retweets-by-tweet-id (path-prefix "/tweet/:tweet-id/retweets")})