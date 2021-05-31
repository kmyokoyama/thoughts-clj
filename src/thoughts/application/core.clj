(ns thoughts.application.core
  (:require [buddy.hashers :as hashers]
            [schema.core :as s])
  (:import (java.time ZonedDateTime)
           (java.util UUID)))

(s/defrecord User [id :- s/Uuid
                   active :- s/Bool
                   name :- s/Str
                   email :- s/Str
                   username :- s/Str
                   following :- s/Int
                   followers :- s/Int])

(s/defrecord Thought [id :- s/Uuid
                      user-id :- s/Uuid
                      text :- s/Str
                      publish-date :- ZonedDateTime
                      likes :- s/Int
                      rethoughts :- s/Int
                      replies :- s/Int])

(s/defrecord Rethought [id :- s/Uuid
                        user-id :- s/Uuid
                        has-comment :- s/Bool
                        comment :- s/Str
                        publish-date :- ZonedDateTime
                        source-thought-id :- s/Uuid])

(s/defrecord ThoughtLike [id :- s/Uuid
                          created-at :- ZonedDateTime
                          user-id :- s/Uuid
                          source-thought-id :- s/Uuid])

(s/defrecord Session [id :- s/Uuid
                      user-id :- s/Uuid
                      created-at :- ZonedDateTime])         ;; TODO: Remove it from here.

(defn sort-by-date
  "Sort the given collection descending by `:publish-date`."
  [coll]
  (sort-by :publish-date #(compare %2 %1) coll))

(defn selected-idx
  "Finds an element and its index in the given collection `coll`,
  extracting values with `key-fn` and comparing them with `comp-fn`.

  Returns a pair of the selected element and its index.

  Example:
  ```clojure
  (def coll [{:a 1 :b 2} {:a 10 :b 20}])

  (selected-idx :a < coll)
  ;=> ({:a 1 :b 2} 0)

  (selected-idx :b > coll)
  ;=> ({:a 10 :b 20} 1)
  ```
  "
  [key-fn comp-fn coll]
  (take 2 (reduce (fn [[curr-sel sel-idx curr-idx] val]
                    (if (nil? curr-sel)
                      [val curr-idx (inc curr-idx)]
                      (if (nil? val)
                        [curr-sel sel-idx (inc curr-idx)]
                        (if (comp-fn (key-fn val) (key-fn curr-sel))
                          [val curr-idx (inc curr-idx)]
                          [curr-sel sel-idx (inc curr-idx)]))))
                  [(first coll) 0 0]
                  coll)))

(defn merge-sorted
  "Merges the sorted collections in `coll` into a sorted collection of length at most `limit`.

  It uses `key-fn` to extract the values and compare them with `comp-fn`.

  Example:
  ```clojure
  (def coll1 [[1 18 20] [5 13 19] [10 12 31]])

  (merge-sorted identity < 5 coll1)
  ;=> [1 5 10 12 13]

  (def coll2 [[{:a 20} {:a 18} {:a 1}] [{:a 19} {:a 13} {:a 5}] [{:a 31} {:a 12} {:a 10}]])

  (merge-sorted :a > 8 coll2)
  ;=> [{:a 31} {:a 20} {:a 19} {:a 18} {:a 13} {:a 12} {:a 10} {:a 5}]
  ```
  "
  [key-fn comp-fn limit coll]
  (loop [acc [] vs (vec coll) n 0]
    (let [[curr idx] (selected-idx key-fn comp-fn (map first vs))]
      (if (or (= n limit) (nil? curr))
        acc
        (recur (conj acc curr) (update vs idx rest) (inc n))))))

(defn merge-by-date
  "Merges date-sorted collections in `coll` into a single date-sorted collection.

  The elements in those collections are supposed to have a field `:publish-date`
  of type descending from `ChronoZonedDateTime` and the collections are already sorted
  by this field.

  The output collection has length at most `limit`."
  [limit coll]
  (merge-sorted :publish-date #(.isAfter %1 %2) limit coll))

(defn new-session
  [user-id]
  (->Session (str (UUID/randomUUID)) user-id (ZonedDateTime/now)))

(defn derive-password
  [password]
  (hashers/derive password))

(defn password-match?
  [password actual-password]
  (hashers/check password actual-password))

(defn extract-hashtags
  "Creates a set of hashtags (without the leading #) extracted from `text`."
  [text]
  (into #{} (->> text (re-seq #"#\w+") (map #(subs % 1)))))

;; Thought-related functions.

(defn new-thought
  [user-id text]
  (let [thought-id (str (UUID/randomUUID))]
    (->Thought thought-id user-id text (ZonedDateTime/now) 0 0 0)))

(defn new-like
  [user-id thought-id]
  (->ThoughtLike (str (UUID/randomUUID)) (ZonedDateTime/now) user-id thought-id))

(defn like
  [thought]
  (update thought :likes inc))

(defn unlike
  [thought]
  (if (pos? (:likes thought))
    (update thought :likes dec)
    thought))

;; Rethought-related functions.

(defn new-rethought
  ([user-id source-thought-id]
   (->Rethought (str (UUID/randomUUID)) user-id false nil (ZonedDateTime/now) source-thought-id))

  ([user-id source-thought-id comment]
   (->Rethought (str (UUID/randomUUID)) user-id true comment (ZonedDateTime/now) source-thought-id)))

(defn rethought
  [rethoughted]
  (update rethoughted :rethoughts inc))

;; Reply-related functions.

(defn reply
  [thought]
  (update thought :replies inc))

;; User-related functions.

(defn new-user
  [name email username]
  (->User (str (UUID/randomUUID)) true name email username 0 0))

(defn follow
  [follower followed]
  (vector (update follower :following inc) (update followed :followers inc)))

(s/defn unfollow :- [(s/one User "follower") (s/one User "followed")]
  [follower :- User
   followed :- User]
  (vector (update follower :following dec) (update followed :followers dec)))