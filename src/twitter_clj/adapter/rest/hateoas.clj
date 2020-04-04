(ns twitter-clj.adapter.rest.hateoas
  (:require [clojure.string :refer [join split]]
            [twitter-clj.adapter.rest.util :refer [join-path]]
            [twitter-clj.adapter.rest.config :refer [routes-map]]))

(defn get-host
  [{:keys [scheme server-name server-port]}]
  (str (name scheme) "://" server-name ":" server-port))

(defn replace-path
  [path replacements]
  (let [replacements-str (zipmap (map str (keys replacements)) (vals replacements))]
    (->> path
         (#(split % #"/"))
         (replace replacements-str)
         (apply join-path))))

(defn add-links
  [req tweet]
  (let [{:keys [id user-id]} tweet]
    (merge tweet {:_links {:self {:href (join-path (get-host req) (replace-path (:get-tweet-by-id routes-map) {:tweet-id id}))}
                             :user {:href (join-path (get-host req) (replace-path (:get-user-by-id routes-map) {:user-id user-id}))}
                             :replies {:href (join-path (get-host req) (replace-path (:get-replies-by-tweet-id routes-map) {:tweet-id id}))}
                             :retweets {:href (join-path (get-host req) (replace-path (:get-retweets-by-tweet-id routes-map) {:tweet-id id}))}}})))