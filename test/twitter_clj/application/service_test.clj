(ns twitter-clj.application.service-test
  (:require [clojure.test :refer :all]
            [twitter-clj.application.port.repository :as repository]
            [twitter-clj.application.test-util :refer :all]
            [twitter-clj.application.port.service :refer :all]
            [twitter-clj.application.service :refer :all]
            [twitter-clj.application.core :as core]
            [clojure.string :as s])
  (:import (clojure.lang ExceptionInfo)))

(deftest new-user
  (testing "Returns true if no user is fetched from repository"
    (let [service (map->Service {})]
      (with-redefs [repository/fetch-users! (fn [_ _] [])]
        (is (new-user? service (random-email))))))

  (testing "Returns false if at least one user is fetched from repository"
    (let [service (map->Service {})]
      (with-redefs [repository/fetch-users! (fn [_ _] [(random-user)])]
        (is (not (new-user? service (random-email))))))))

(deftest user-exists
  (testing "Returns true if the specified user is returned from repository"
    (let [service (map->Service {})
          user (random-user)
          user-id (:id user)]
      (with-redefs [repository/fetch-users! (fn [_ _] [user])]
        (is (user-exists? service user-id)))))

  (testing "Returns false if no user is returned from repository"
    (let [service (map->Service {})
          user (random-user)
          user-id (:id user)]
      (with-redefs [repository/fetch-users! (fn [_ _] [])]
        (is (not (user-exists? service user-id)))))))

(deftest password-match
  (testing "Returns true if password belongs to user"
    (let [service (map->Service {})
          password (random-password)
          hashed-password (core/derive-password password)]
      (with-redefs [repository/fetch-password! (fn [_ _] hashed-password)]
        (is (password-match? service (random-uuid) password)))))

  (testing "Returns false if password does not belong to user"
    (let [service (map->Service {})
          password (random-password)]
      (with-redefs [repository/fetch-password! (fn [_ _] nil)]
        (is (not (password-match? service (random-uuid) password)))))))

(deftest test-create-user
  (testing "Throws an exception if user email is already registered"
    (let [service (map->Service {})]
      (with-redefs [new-user? (fn [_ _] false)]
        (is (thrown-with-msg? ExceptionInfo #".*email.*already exists"
                              (create-user service
                                           (random-fullname)
                                           (random-email)
                                           (random-username)
                                           (random-password)))))))

  (testing "Throws an exception if username is already taken"
    (let [service (map->Service {})]
      (with-redefs [new-user? (fn [_ _] true)
                    username-available? (fn [_ _] false)]
        (is (thrown-with-msg? ExceptionInfo #".*username.*already exists"
                              (create-user service
                                           (random-fullname)
                                           (random-email)
                                           (random-username)
                                           (random-password)))))))

  (testing "Returns the created user"
    (let [service (map->Service {})
          name (random-fullname)
          email (random-email)
          username (random-username)
          password (random-password)]
      (with-redefs [new-user? (fn [_ _] true)
                    username-available? (fn [_ _] true)
                    repository/update-user! (fn [_ _])
                    repository/update-password! (fn [_ _ _])]
        (let [user (create-user service name email username password)]
          (is (= (s/lower-case name) (:name user)))
          (is (= (s/lower-case email) (:email user)))
          (is (= (s/lower-case username) (:username user)))
          (is (zero? (:following user)))
          (is (zero? (:followers user))))))))

(deftest test-get-user-by-id
  (testing "Throws an exception if user ID is not found"
    (let [service (map->Service {})]
      (with-redefs [repository/fetch-users! (fn [_ _]) []]
        (is (thrown-with-msg? ExceptionInfo #".*User.*not found" (get-user-by-id service (random-uuid)))))))

  (testing "Returns the user with the given ID as it is on repository if it exists"
    (let [service (map->Service {})
          user (random-user)
          user-id (:id user)]
      (with-redefs [repository/fetch-users! (fn [_ {uid :user-id}]
                                              (if (= uid user-id)
                                                [user]
                                                []))]
        (is (= user (get-user-by-id service user-id)))))))