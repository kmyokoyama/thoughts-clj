(ns twitter-clj.application.service-test
  (:require [clojure.test :refer :all]
            [twitter-clj.application.service :refer :all]
            [midje.sweet :refer :all]
            [twitter-clj.application.test-util :refer :all]
            [twitter-clj.application.core :as core]
            [twitter-clj.application.port.repository :as repository :refer [Repository]])
  (:import [clojure.lang IExceptionInfo]))

(facts "About `add-user`"
      (fact "It returns a fresh new user"
            (let [fullname (random-fullname)
                  email (random-email)
                  username (random-username)
                  stub (reify Repository
                         (update-user! [_ user] user))
                  service {:repository stub}
                  user (add-user service fullname email username)]
              (:name user) => fullname
              (:email user) => email
              (:username user) => username)
            (against-background
                (new-user? anything anything) => true))

      (fact "It throws an exception when user already exists"
            (let [fullname (random-fullname)
                  email (random-email)
                  username (random-username)
                  stub (reify Repository
                         (update-user! [_ user] user))
                  service {:repository stub}]
              (add-user service fullname email username) => (throws IExceptionInfo))
            (against-background
              (new-user? anything anything) => false)))

(facts "About `get-user-by-id`"
       (fact "It returns the required user"
             (let [user (core/new-user (random-fullname) (random-email) (random-username))
                   stub (reify Repository
                          (fetch-users! [_ _ _] user))
                   service {:repository stub}]
               (get-user-by-id service (:id user)) => user))

       (fact "It throws an exception when user does not exist"
             (let [user (core/new-user (random-fullname) (random-email) (random-username))
                   stub (reify Repository
                          (fetch-users! [_ _ _] nil))
                   service {:repository stub}]
               (get-user-by-id service (:id user)) => (throws IExceptionInfo))))

(facts "About `add-tweet`"
       (fact "It returns a fresh new tweet"
             (let [user-id (random-uuid)
                   text (random-text)
                   stub (reify Repository
                          (repository/update-tweet! [_ tweet] tweet))
                   service {:repository stub}
                   tweet (add-tweet service user-id text)]
               (:text tweet) => text
               ((juxt :likes :retweets :replies) tweet) => [0 0 0])
             (against-background
                (user-exists? anything anything) => true))

       (fact "It throws an exception when user does not exist"
             (let [user-id  (random-uuid)
                   text (random-text)
                   stub (reify Repository
                          (fetch-users! [_ _ _] nil))
                   service {:repository stub}]
               (add-tweet service user-id text) => (throws IExceptionInfo))))