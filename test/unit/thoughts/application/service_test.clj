(ns unit.thoughts.application.service-test
  (:require [clojure.string :as s]
            [clojure.test :refer :all]
            [thoughts.application.core :as core]
            [thoughts.application.service :as service]
            [thoughts.port.repository :as p.repository]
            [thoughts.port.service :as p.service]
            [unit.thoughts.application.helper :as application.helper])
  (:import (clojure.lang ExceptionInfo)))

(deftest ^:integration new-user
  (testing "Returns true if no user is fetched from repository"
    (let [service (service/map->Service {})]
      (with-redefs [p.repository/fetch-users! (fn [_ _] [])]
        (is (p.service/new-user? service (application.helper/random-email))))))

  (testing "Returns false if at least one user is fetched from repository"
    (let [service (service/map->Service {})]
      (with-redefs [p.repository/fetch-users! (fn [_ _] [(application.helper/random-user)])]
        (is (not (p.service/new-user? service (application.helper/random-email))))))))

(deftest ^:integration user-exists
  (testing "Returns true if the specified user is returned from repository"
    (let [service (service/map->Service {})
          user (application.helper/random-user)
          user-id (:id user)]
      (with-redefs [p.repository/fetch-users! (fn [_ _] [user])]
        (is (p.service/user-exists? service user-id)))))

  (testing "Returns false if no user is returned from repository"
    (let [service (service/map->Service {})
          user (application.helper/random-user)
          user-id (:id user)]
      (with-redefs [p.repository/fetch-users! (fn [_ _] [])]
        (is (not (p.service/user-exists? service user-id)))))))

(deftest ^:integration password-match
  (testing "Returns true if password belongs to user"
    (let [password (application.helper/random-password)
          hashed-password (core/derive-password password)
          mock-repository (reify p.repository/UserRepository (p.repository/fetch-password! [_ _] hashed-password))
          service (service/map->Service {:repository mock-repository})]
      (is (p.service/password-match? service (application.helper/random-uuid) password))))

  (testing "Returns false if password does not belong to user"
    (let [password (application.helper/random-password)
          mock-repository (reify p.repository/UserRepository (p.repository/fetch-password! [_ _] nil))
          service (service/map->Service {:repository mock-repository})]
      (is (not (p.service/password-match? service (application.helper/random-uuid) password))))))

(deftest ^:integration test-create-user
  (testing "Throws an exception if user email is already registered"
    (let [mock-repository (reify p.repository/UserRepository (p.repository/fetch-users! [_ _] [:mock-user]))
          service (service/map->Service {:repository mock-repository})]
      (is (thrown-with-msg? ExceptionInfo #".*email.*already exists"
                            (p.service/create-user service
                                                   (application.helper/random-fullname)
                                                   (application.helper/random-email)
                                                   (application.helper/random-username)
                                                   (application.helper/random-password))))))

  (testing "Throws an exception if username is already taken"
    (let [mock-repository (reify p.repository/UserRepository
                            (p.repository/fetch-users! [_ criteria]
                              (cond
                                (:id criteria) []
                                (:username criteria) [:mock-user])))
          service (service/map->Service {:repository mock-repository})]
      (is (thrown-with-msg? ExceptionInfo #".*username.*already exists"
                            (p.service/create-user service
                                                   (application.helper/random-fullname)
                                                   (application.helper/random-email)
                                                   (application.helper/random-username)
                                                   (application.helper/random-password))))))

  (testing "Returns the created user"
    (let [mock-repository (reify p.repository/UserRepository
                            (p.repository/fetch-users! [_ _] [])
                            (p.repository/update-user! [_ _] nil)
                            (p.repository/update-password! [_ _ _] nil))
          service (service/map->Service {:repository mock-repository})
          name (application.helper/random-fullname)
          email (application.helper/random-email)
          username (application.helper/random-username)
          password (application.helper/random-password)]
      (let [user (p.service/create-user service name email username password)]
        (is (= (s/lower-case name) (:name user)))
        (is (= (s/lower-case email) (:email user)))
        (is (= (s/lower-case username) (:username user)))
        (is (zero? (:following user)))
        (is (zero? (:followers user)))))))

(deftest ^:integration test-get-user-by-id
  (testing "Throws an exception if user ID is not found"
    (let [mock-repository (reify p.repository/UserRepository
                            (p.repository/fetch-users! [_ _] []))
          service (service/map->Service {:repository mock-repository})]
      (is (thrown-with-msg? ExceptionInfo #".*User.*not found" (p.service/get-user-by-id service (application.helper/random-uuid))))))

  (testing "Returns the user with the given ID as it is on repository if it exists"
    (let [user (application.helper/random-user)
          user-id (:id user)
          mock-repository (reify p.repository/UserRepository
                            (p.repository/fetch-users! [_ {uid :user-id}]
                              (if (= uid user-id)
                                [user]
                                [])))
          service (service/map->Service {:repository mock-repository})]
      (is (= user (p.service/get-user-by-id service user-id))))))