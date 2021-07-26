(ns unit.thoughts.application.core-test
  (:require [clojure.test :refer :all]
            [thoughts.application.core :as core]
            [unit.thoughts.application.helper :as application.helper]
            [matcher-combinators.test :refer [match?]]))

(deftest ^:unit new-thought-test
  (testing "new-thought creates a fresh new thought entity"
    (is (match? {:likes 0
                 :rethoughts 0
                 :replies 0
                 :user-id application.helper/uuid?
                 :id application.helper/uuid?
                 :text string?}
                (core/new-thought (application.helper/random-uuid) (application.helper/random-text))))))

(deftest ^:unit new-like-test
  (testing "new-like creates a fresh new like entity"
    (is (match? {:id application.helper/uuid?
                 :user-id application.helper/uuid?
                 :source-thought-id application.helper/uuid?}
                (core/new-like (application.helper/random-uuid) (application.helper/random-uuid))))))

(deftest ^:unit new-rethought-test
  (testing "new-rethought creates a fresh new rethought entity"
    (is (match? {:id application.helper/uuid?
                 :user-id application.helper/uuid?
                 :has-comment false
                 :comment nil
                 :source-thought-id application.helper/uuid?}
                (core/new-rethought (application.helper/random-uuid) (application.helper/random-uuid))))))

(deftest ^:unit new-user-test
  (testing "new-user creates a fresh new user"
    (is (match? {:id application.helper/uuid?
                 :active true
                 :name string?
                 :email string?
                 :username string?}
                (core/new-user (application.helper/random-fullname)
                               (application.helper/random-email)
                               (application.helper/random-username))))))