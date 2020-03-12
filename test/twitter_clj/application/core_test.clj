(ns twitter-clj.application.core-test
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [clojure.data.generators :as random]
            [twitter-clj.application.core :refer :all])
  (:import (java.time ZonedDateTime)))

(defn- new-random-tweet [] (new-tweet (random/uuid) (random/string)))

(defn- now [] (ZonedDateTime/now))

(defn- fresh-sharing-stats?
  [tweet]
  (every? zero? ((juxt :likes :retweets :replies) tweet)))

(defmacro equal-except-for
  [expected actual & exceptions]
  `(let [keys-set# (comp set keys)
         expected-keys# (keys-set# ~expected)
         actual-keys# (keys-set# ~actual)
         exception-keys# (set '~exceptions)
         valid-keys# (set/difference (set/intersection expected-keys# actual-keys#) exception-keys#)]
     (every? identity (vec (doall (map #(is (= (% ~expected) (% ~actual))) valid-keys#))))))

;; Tweet-related tests.

(deftest like-increase-by-one
  (testing "When like, then should increase likes by one."
    (let [tweet (->Tweet (random/uuid) (random/uuid) "This is my tweet" (now) 10 0 0 (random/uuid))
          liked-tweet (like tweet)]
      (equal-except-for tweet liked-tweet :likes)
      (is (= 11 (:likes liked-tweet))))))

(deftest unlike-decrease-by-one
  (testing "When unlike, then should decrease likes by one."
    (let [tweet (->Tweet (random/uuid) (random/uuid) "This is my tweet" (now) 10 0 0 (random/uuid))
          unliked-tweet (unlike tweet)]
      (equal-except-for tweet unliked-tweet :likes)
      (is (= 9 (:likes (unlike tweet)))))))

;; Retweet-related tests.

(deftest retweet-with-comment-creates-new-tweet
  (testing "When retweets with comment, then creates a new tweet."
    (let [original-tweet (new-random-tweet)
          retweet-user-id (random/uuid)
          comment (random/string)
          [retweet, retweeted] (retweet-with-comment retweet-user-id comment original-tweet)]
      (is (= (inc (:retweets original-tweet)) (:retweets retweeted)))
      (is (= (:original-tweet-id retweet) (:id original-tweet)))
      (is (= (get-in retweet [:tweet :user-id]) retweet-user-id))
      (is (= (get-in retweet [:tweet :text]) comment))
      (is (fresh-sharing-stats? (:tweet retweet))))))

(deftest retweet-without-comment-links-original-tweet
  (testing "When retweets without comment, then links original tweet."
    (let [original-tweet (new-random-tweet)
          retweet-user-id (random/uuid)
          [retweet', retweeted] (retweet retweet-user-id original-tweet)]
      (is (= (inc (:retweets original-tweet)) (:retweets retweeted)))
      (is (= (:original-tweet-id retweet') (:id original-tweet)))
      (is (= (:user-id retweet') retweet-user-id)))))

;; Thread-related tests.

(deftest reply-links-tweets
  (testing "When replies, link new tweets to source tweet."
    (let [source-tweet (new-random-tweet)
          thread (new-thread (:id source-tweet))
          source-tweet' (assoc source-tweet :thread-id (:id thread))
          reply-tweets (repeatedly 5 (fn [] (new-random-tweet)))
          reply-tweet-ids (map :id reply-tweets)
          final-thread (reduce (fn [t r] (nth (reply r source-tweet' t) 2)) thread reply-tweets)
          final-tweet-ids (:tweet-replies final-thread)]
      (is (= 5 (count (:tweet-replies final-thread))))
      (is (= (set reply-tweet-ids) (set final-tweet-ids))))))


