(ns twitter-clj.adapter.rest.hateoas
  (:require [clojure.string :refer [join split]]
            [twitter-clj.adapter.rest.util :refer [join-path]]
            [twitter-clj.adapter.rest.config :refer [routes-map]]))

(defn- get-host
  [{:keys [scheme server-name server-port]}]
  (str (name scheme) "://" server-name ":" server-port))

(defn- replace-path
  [path replacements]
  (let [replacements-str (zipmap (map str (keys replacements)) (vals replacements))]
    (->> path
         (#(split % #"/"))
         (replace replacements-str)
         (apply join-path))))

(defn- make-links-map
  [host response links]
  (assoc response :_links
                  (zipmap (keys links)
                          (map (fn [val] (let [path-key (val 0)
                                               path-variables (val 1)]
                                           {:href (join-path host (replace-path (path-key routes-map) path-variables))}))
                               (vals links)))))

(defmulti add-links (fn [selector-key & _args] selector-key))

(defmethod add-links :tweet
  [_ req tweet]
  (let [{:keys [id user-id]} tweet]
    (make-links-map (get-host req) tweet {:self  [:get-tweet-by-id {:tweet-id id}]
                                          :user     [:get-user-by-id {:user-id user-id}]
                                          :replies  [:get-replies-by-tweet-id {:tweet-id id}]
                                          :retweets [:get-retweets-by-tweet-id {:tweet-id id}]})))

(defmethod add-links :reply
  [_ req source-tweet-id tweet]
  (let [{:keys [id user-id]} tweet]
    (make-links-map (get-host req) tweet {:self  [:get-tweet-by-id {:tweet-id id}]
                                          :user     [:get-user-by-id {:user-id user-id}]
                                          :replies  [:get-replies-by-tweet-id {:tweet-id id}]
                                          :retweets [:get-retweets-by-tweet-id {:tweet-id id}]
                                          :source-tweet [:get-tweet-by-id {:tweet-id source-tweet-id}]})))