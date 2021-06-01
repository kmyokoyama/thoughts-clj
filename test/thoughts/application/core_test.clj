(ns thoughts.application.core-test
  (:require [clojure.test :refer :all]
            [thoughts.application.core :refer :all]
            [thoughts.application.test-util :refer :all]
            [midje.sweet :refer :all]))

(fact "`new-thought` creates a fresh new thought entity"
      (let [user-id (random-uuid)
            text (random-text)
            thought (new-thought user-id text)]
        (:user-id thought) => user-id
        (:likes thought) => 0
        (:rethoughts thought) => 0
        (:replies thought) => 0
        (:id thought) => uuid-format?))

(fact "`new-like` creates a fresh new like entity"
      (let [user-id (random-uuid)
            thought-id (random-uuid)
            like (new-like user-id thought-id)]
        (:id like) => uuid-format?
        (:user-id like) => user-id
        (:source-thought-id like) => thought-id))

(facts "About `new-rethought`"
       (fact "It creates a fresh new rethought entity"
             (let [user-id (random-uuid)
                   source-thought-id (random-uuid)
                   rethought (new-rethought user-id source-thought-id)]
               (:id rethought) => uuid-format?
               (:user-id rethought) => user-id
               (:has-comment rethought) => false
               (:comment rethought) => nil
               (:source-thought-id rethought) => source-thought-id))

       (fact "It creates a fresh new rethought entity with a comment"
             (let [user-id (random-uuid)
                   source-thought-id (random-uuid)
                   comment (random-text)
                   rethought (new-rethought user-id source-thought-id comment)]
               (:id rethought) => uuid-format?
               (:user-id rethought) => user-id
               (:has-comment rethought) => true
               (:comment rethought) => comment
               (:source-thought-id rethought) => source-thought-id)))

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
      (let [thought (->Thought (random-uuid) (random-uuid) "This is my thought" (now) 10 0 0)
            updated-thought (like thought)]
        (:likes updated-thought)) => 11)

(facts "About `unlike`"
       (fact "It decreases likes counting by one"
             (let [thought (->Thought (random-uuid) (random-uuid) "This is my thought" (now) 10 0 0)
                   updated-thought (unlike thought)]
               (:likes updated-thought)) => 9)

       (fact "It does not decrease zero likes counting"
             (let [thought (->Thought (random-uuid) (random-uuid) "This is my thought" (now) 0 0 0)
                   updated-thought (unlike thought)]
               (:likes updated-thought)) => 0))

(fact "`rethought` increases rethoughts counting by one"
      (let [thought (->Thought (random-uuid) (random-uuid) "This is my thought" (now) 0 10 0)
            updated-thought (rethought thought)]
        (:rethoughts updated-thought)) => 11)

(fact "`reply` increases replies counting by one"
      (let [thought (->Thought (random-uuid) (random-uuid) "This is my thought" (now) 0 0 10)
            updated-thought (reply thought)]
        (:replies updated-thought)) => 11)
