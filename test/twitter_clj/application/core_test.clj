(ns twitter-clj.application.core-test
  (:require [clojure.test :refer :all]
            [twitter-clj.application.core :refer :all]
            [twitter-clj.application.test-util :refer :all]
            [midje.sweet :refer :all]))

(fact "`new-tweet` creates a fresh new tweet entity"
      (let [user-id (random-uuid)
            text (random-text)
            tweet (new-tweet user-id text)]
        (:user-id tweet) => user-id
        (:likes tweet) => 0
        (:retweets tweet) => 0
        (:replies tweet) => 0
        (:id tweet) => uuid-format?))

(fact "`new-like` creates a fresh new like entity"
      (let [user-id (random-uuid)
            tweet-id (random-uuid)
            like (new-like user-id tweet-id)]
        (:id like) => uuid-format?
        (:user-id like) => user-id
        (:tweet-id like) => tweet-id))

(facts "About `new-retweet`"
       (fact "It creates a fresh new retweet entity"
             (let [user-id (random-uuid)
                   retweeted (new-tweet (random-uuid) (random-text))
                   retweet (new-retweet user-id retweeted)]
               (:id retweet) => uuid-format?
               (:user-id retweet) => user-id
               (:has-comment retweet) => false
               (:comment retweet) => nil
               (:tweet retweet) => retweeted))

       (fact "It creates a fresh new retweet entity with a comment"
             (let [user-id (random-uuid)
                   retweeted (new-tweet (random-uuid) (random-text))
                   comment (random-text)
                   retweet (new-retweet user-id retweeted comment)]
               (:id retweet) => uuid-format?
               (:user-id retweet) => user-id
               (:has-comment retweet) => true
               (:comment retweet) => comment
               (:tweet retweet) => retweeted)))

(fact "`new-user` creates a fresh new user"
      (let [name (random-fullname)
            email (random-email)
            username (random-username)
            user (new-user name email username)]
        (:id user) => uuid-format?
        (:active user) => true
        (:name user) => name
        (:email user) => email
        (:username user) => username))

(fact "`like` increases likes counting by one"
      (let [tweet (->Tweet (random-uuid) (random-uuid) "This is my tweet" (now) 10 0 0)
            updated-tweet (like tweet)]
        (:likes updated-tweet)) => 11)

(facts "About `unlike`"
       (fact "It decreases likes counting by one"
             (let [tweet (->Tweet (random-uuid) (random-uuid) "This is my tweet" (now) 10 0 0)
                   updated-tweet (unlike tweet)]
               (:likes updated-tweet)) => 9)

       (fact "It does not decrease zero likes counting"
             (let [tweet (->Tweet (random-uuid) (random-uuid) "This is my tweet" (now) 0 0 0)
                   updated-tweet (unlike tweet)]
               (:likes updated-tweet)) => 0))

(fact "`retweet` increases retweets counting by one"
      (let [tweet (->Tweet (random-uuid) (random-uuid) "This is my tweet" (now) 0 10 0)
            updated-tweet (retweet tweet)]
        (:retweets updated-tweet)) => 11)

(fact "`reply` increases replies counting by one"
      (let [tweet (->Tweet (random-uuid) (random-uuid) "This is my tweet" (now) 0 0 10)
            updated-tweet (reply tweet)]
        (:replies updated-tweet)) => 11)
