(ns twitter-clj.application.service-test
  (:require [clojure.test :refer :all]
            [twitter-clj.application.port.repository :as repository]
            [twitter-clj.application.test-util :refer :all]
            [twitter-clj.application.service :refer :all]
            [twitter-clj.application.core :as core]
            [buddy.hashers :as hashers]))

(deftest new-user
  (testing "new-user? returns true if no user is fetched from repository"
    (let [stub-repository (reify repository/Repository
                            (fetch-users! [_ _] []))        ;; No user is fetched.
          new? (new-user? {:repository stub-repository} (random-email))]
      (is new?)))

  (testing "new-user? returns false if at least one user is fetched from repository"
    (let [stub-repository (reify repository/Repository
                            (fetch-users! [_ _] [(random-user)])) ;; One user is fetched.
          new? (new-user? {:repository stub-repository} (random-email))]
      (is (not new?)))))

(deftest user-exists
  (testing "user-exists? returns true if the specified user is returned from repository"
    (let [stub-repository (reify repository/Repository
                            (fetch-users! [_ _] [(random-user)])) ;; One user is fetched.
          new? (user-exists? {:repository stub-repository} (random-uuid))]
      (is new?)))

  (testing "user-exists? returns false if no user is returned from repository"
    (let [stub-repository (reify repository/Repository
                            (fetch-users! [_ _] []))        ;; No user is fetched.
          new? (user-exists? {:repository stub-repository} (random-uuid))]
      (is (not new?)))))

(deftest password-match
  (testing "password-match? returns true if password belongs to user"
    (let [password (random-password)
          hashed-password (core/derive-password password)
          stub-repository (reify repository/Repository
                            (fetch-password! [_ _] hashed-password))
          match? (password-match? {:repository stub-repository} (random-uuid) password)]
      (is match?))))

(deftest logged-in
  (testing "logged-in? returns true if there is a session associated with the user"
    (let [stub-repository (reify repository/Repository
                            (fetch-sessions! [_ _] ["session"]))
          logged? (logged-in? {:repository stub-repository} (random-uuid))]
      (is logged?)))

  (testing "logged-in? returns false if there is no session associated with the user"
    (let [stub-repository (reify repository/Repository
                            (fetch-sessions! [_ _] []))
          logged? (logged-in? {:repository stub-repository} (random-uuid))]
      (is (not logged?)))))