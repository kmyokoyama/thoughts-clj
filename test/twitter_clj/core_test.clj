(ns twitter-clj.core-test
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [twitter-clj.core :refer :all])
  (:import (java.util UUID)
           (java.time ZonedDateTime)))

(defn random-uuid [] (UUID/randomUUID))
(defn now [] (ZonedDateTime/now))

(defmacro equal-except-for
  [expected actual & exceptions]
  `(let [keys-set# (comp set keys)
         expected-keys# (keys-set# ~expected)
         actual-keys# (keys-set# ~actual)
         exception-keys# (set '~exceptions)
         valid-keys# (set/difference (set/intersection expected-keys# actual-keys#) exception-keys#)]
     (every? identity (vec (doall (map #(is (= (% ~expected) (% ~actual))) valid-keys#))))))

(deftest like-increase-by-one
  (testing "When like, then should increase likes by one."
    (let [tweet (->Tweet (random-uuid) (random-uuid) "This is my tweet" (now) 10 0 0 (random-uuid))
          liked-tweet (like tweet)]
      (equal-except-for tweet liked-tweet :likes)
      (is (= 11 (:likes liked-tweet))))))

(deftest unlike-decrease-by-one
  (testing "When unlike, then should decrease likes by one."
    (let [tweet (->Tweet (random-uuid) (random-uuid) "This is my tweet" (now) 10 0 0 (random-uuid))
          unliked-tweet (unlike tweet)]
      (equal-except-for tweet unliked-tweet :likes)
      (is (= 9 (:likes (unlike tweet)))))))

(deftest retweet-creates-new-tweet-from-original
  (testing "When retweets, then creates a tweet with same text and
  increases rewteets counting from the original tweet"
    (let [original-tweet (new-tweet (random-uuid) "This is my text")
          [re-tweet, retweeted] (retweet (:user-id original-tweet) original-tweet)]
      (is (= (inc (:retweets original-tweet)) (:retweets retweeted)))
      (equal-except-for retweeted re-tweet :id :publish-date :likes :retweets))))
