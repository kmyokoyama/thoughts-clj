(ns twitter-clj.application.service-test
  (:require [clojure.test :refer :all]
            [twitter-clj.application.port.repository :as repository]
            [twitter-clj.application.test-util :refer :all]
            [twitter-clj.application.service :refer :all]
            [twitter-clj.application.port.service :refer :all]
            [twitter-clj.application.core :as core]))

(deftest test-new-user
  (testing "new-user? returns true if no user is fetched from repository"
    (let [stub-repository (reify repository/UserRepository
                            (fetch-users! [_ _] []))        ;; No user is fetched.
          service (map->Service {:repository stub-repository})
          new? (new-user? service (random-email))]
      (is new?)))

  (testing "new-user? returns false if at least one user is fetched from repository"
    (let [stub-repository (reify repository/UserRepository
                            (fetch-users! [_ _] [(random-user)])) ;; One user is fetched.
          service (map->Service {:repository stub-repository})
          new? (new-user? service (random-email))]
      (is (not new?)))))

(deftest test-user-exists
  (testing "user-exists? returns true if the specified user is returned from repository"
    (let [stub-repository (reify repository/UserRepository
                            (fetch-users! [_ _] [(random-user)])) ;; One user is fetched.
          service (map->Service {:repository stub-repository})
          new? (user-exists? service (random-uuid))]
      (is new?)))

  (testing "user-exists? returns false if no user is returned from repository"
    (let [stub-repository (reify repository/UserRepository
                            (fetch-users! [_ _] [])) ;; No user is fetched.
          service (map->Service {:repository stub-repository})
          new? (user-exists? service (random-uuid))]
      (is (not new?)))))

(deftest test-password-match
  (testing "password-match? returns true if password belongs to user"
    (let [password (random-password)
          hashed-password (core/derive-password password)
          stub-repository (reify repository/UserRepository
                            (fetch-password! [_ _] hashed-password))
          service (map->Service {:repository stub-repository})
          match? (password-match? service (random-uuid) password)]
      (is match?))))