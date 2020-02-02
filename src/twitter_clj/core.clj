(ns twitter-clj.core
  (:import (java.util UUID)
           (java.time ZonedDateTime))
  (:require [clojure.string :as s])
  (:require [twitter-clj.infra.in-mem :as infra.in-mem]))

(defrecord User [id active name email nickname])

(defrecord Tweet [id user-id text publish-date likes retweets])

;; Tweet-related functions

(defn like
  [tweet]
  (update tweet :likes inc))

(defn unlike
  [tweet]
  (update tweet :likes dec))

(defn new-tweet
  [user-id text]
  (->Tweet (UUID/randomUUID) user-id text (ZonedDateTime/now) 0 0))

(defn retweet
  [user-id tweet]
  [(new-tweet user-id (:text tweet))
   (update tweet :retweets inc)])

(defn update-tweet!
  [tweet system-map]
  (let [update-fn! (:update-tweet-fn! system-map)]
    (update-fn! tweet)
    tweet))

;; User-related functions

(defn new-user
  [name email nickname]
  (->User (UUID/randomUUID) true name email nickname))

(defn update-user!
  [user system-map]
  (let [update-user-fn! (:update-user-fn! system-map)]
    (update-user-fn! user)
    user))

;; Debug-mode functions.

(defn inspect-users!
  [system-map]
  (let [inspect-users-fn! (:inspect-users-fn! system-map)]
    (inspect-users-fn!)))

(defn inspect-tweets!
  [system-map]
  (let [inspect-tweets-fn! (:inspect-tweets-fn! system-map)]
     (inspect-tweets-fn!)))

(def ^:dynamic *system-map* infra.in-mem/system-map)

(defn -main
  [& _args]
  (let [{user-id :id :as user} (-> (new-user "Kazuki Yokoyama" "yokoyama.km@gmail.com" "kmyokoyama")
                                   (update-user! *system-map*))]
    (println (inspect-users! *system-map*))

    (let [tweet (-> (new-tweet user-id "My first tweet.")
                    (update-tweet! *system-map*)
                    (like)
                    (like)
                    (unlike)
                    (update-tweet! *system-map*))]
      (println (inspect-tweets! *system-map*))

      (let [{user-id' :id} (new-user "John Dun" "john.dun@gmail.com" "johndun")]
        (doall (map #(update-tweet! % *system-map*) (retweet user-id' tweet)))

        (println (inspect-tweets! *system-map*))))))