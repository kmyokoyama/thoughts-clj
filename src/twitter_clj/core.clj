(ns twitter-clj.core
  (:import (java.util UUID)
           (java.time ZonedDateTime))
  (:require [clojure.string :as s])
  (:require [twitter-clj.infra.in-mem :as infra.in-mem]))

(defrecord User [id active name email nickname])

(defrecord Tweet [id user-id text publish-date likes retweets replies thread-id])

(defrecord TwitterThread [id source-tweet-id tweet-replies])

;; Tweet-related functions

(defn like
  [tweet]
  (update tweet :likes inc))

(defn unlike
  [{likes :likes :as tweet}]
  (if (pos? likes)
    (update tweet :likes dec)
    tweet))

(defn new-tweet
  [user-id text]
  (->Tweet (UUID/randomUUID) user-id text (ZonedDateTime/now) 0 0 0 nil))

(defn retweet
  [user-id tweet]
  [(new-tweet user-id (:text tweet))
   (update tweet :retweets inc)])

(defn update-tweet!
  [tweet system-map]
  (let [update! (:update-tweet-fn! system-map)]
    (update! tweet))
  tweet)

;; Thread-related functions.

(defn new-thread
  [source-tweet-id]
  (->TwitterThread (UUID/randomUUID) source-tweet-id []))

(defn add-reply-tweet-to-thread
  [thread tweet-id]
  (update thread :tweet-replies conj tweet-id))

(defn add-thread-to-source-tweet
  [tweet thread-id]
  (assoc tweet :thread-id thread-id))

(defn reply
  [reply-tweet source-tweet thread]
  (let [thread' (-> thread (add-reply-tweet-to-thread (:id reply-tweet)))
        source-tweet' (-> source-tweet
                          (add-thread-to-source-tweet (:id thread'))
                          (update :replies inc))]
    [reply-tweet source-tweet' thread']))

(defn fetch-thread!
  [source-tweet system-map]
  (let [thread-id (:thread-id source-tweet)]
    (if (nil? thread-id)
      (new-thread (:id source-tweet))
      (let [fetch-thread-by-id! (:fetch-thread-by-id-fn! system-map)]
        (fetch-thread-by-id! thread-id)))))

(defn update-thread!
  [thread system-map]
  (let [update-thread! (:update-thread-fn! system-map)]
    (update-thread! thread))
  thread)

;; User-related functions

(defn new-user
  [name email nickname]
  (->User (UUID/randomUUID) true name email nickname))

(defn update-user!
  [user system-map]
  (let [update-user! (:update-user-fn! system-map)]
    (update-user! user))
  user)

;; Debug-mode functions.

(defn inspect-users!
  [system-map]
  (let [inspect-users! (:inspect-users-fn! system-map)]
    (inspect-users!)))

(defn inspect-tweets!
  [system-map]
  (let [inspect-tweets! (:inspect-tweets-fn! system-map)]
     (inspect-tweets!)))

(defn inspect-threads!
  [system-map]
  (let [inspect-threads! (:inspect-threads-fn! system-map)]
    (inspect-threads!)))

(def ^:dynamic *system-map* infra.in-mem/system-map)

(defn third [x] (nth x 2))

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

      (let [{user-id' :id} (new-user "John Dun" "john.dun@gmail.com" "johndun")
            tweet (second (map #(update-tweet! % *system-map*) (retweet user-id' tweet)))]

        (println (inspect-tweets! *system-map*))

        (let [thread (fetch-thread! tweet *system-map*)
              reply' (reply (new-tweet user-id' "This is a reply.") tweet thread)
              reply-tweet (first reply')
              source-tweet (second reply')
              thread' (third reply')]
          (update-tweet! reply-tweet *system-map*)
          (update-tweet! source-tweet *system-map*)
          (update-thread! thread' *system-map*)

          (println (inspect-tweets! *system-map*))
          (println (inspect-threads! *system-map*)))))))
