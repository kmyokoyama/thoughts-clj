(ns twitter-clj.adapter.rest.rest-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [twitter-clj.adapter.rest.test_utils :refer :all]
            [twitter-clj.adapter.rest.test_component :refer [start-test-system! stop-test-system!]]
            [twitter-clj.adapter.rest.test_configuration :refer [system-config]]))

(use-fixtures :each (fn [f]
                      (let [system (start-test-system! system-config)]
                        (f)
                        (stop-test-system! system))))

(deftest add-single-user
  (testing "Add a single user"
    (let [response (post-json (resource "user") (new-user))]
      (is (= "success" (:status (body-as-json response))))
      (is (= 201 (:status response)))))) ;; HTTP 201 Created.

(deftest add-single-tweet
  (testing "Add a single tweet"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])
          text "My first tweet"
          response (post-json (resource "tweet") (new-tweet user-id text))
          body (body-as-json response)
          result (:result body)]
      (is (= "success" (:status body)))
      (is (= 201 (:status response))) ;; HTTP 201 Created.
      (is (= user-id (:user-id result)))
      (is (= text (:text result)))
      (is (= 0 (:likes result) (:retweets result) (:replies result))))))

(deftest get-tweets-from-user
  (testing "Get two tweets from the same user"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])
          first-tweet (post-json (resource "tweet") (new-tweet user-id))
          second-tweet (post-json (resource "tweet") (new-tweet user-id))
          first-tweet-id (get-in (body-as-json first-tweet) [:result :id])
          second-tweet-id (get-in (body-as-json second-tweet) [:result :id])
          response (client/get (resource "tweet") {:query-params {:user-id user-id}})
          body (body-as-json response)
          result (:result body)]
      (is (= "success" (:status body)))
      (is (= 200 (:status response))) ;; HTTP 200 OK.
      (is (= 2 (count result)))
      (is (= #{first-tweet-id second-tweet-id} (into #{} (map :id result)))))))

(deftest get-empty-tweets
  (testing "Get tweets returns no tweet if user has not tweet yet"
    (let [user (post-json (resource "user") (new-user))
          user-id (get-in (body-as-json user) [:result :id])]
      ;; No tweet.
      (let [response (client/get (resource "tweet") {:query-params {:user-id user-id}})
            body (body-as-json response)
            result (:result body)]
        (is (= "success" (:status body)))
        (is (= 200 (:status response))) ;; HTTP 200 OK.
        (is (= 0 (count result)))))))

(deftest get-existing-user-by-id
  (testing "Get an existing user returns successfully"
    (let [expected-user {:name "john" :email "john@gmail" :nickname "johnddoe"}
          create-user-response (post-json (resource "user") expected-user)
          user-id (get-in (body-as-json create-user-response) [:result :id])
          get-user-response (client/get (resource (str "user/" user-id)))
          body (body-as-json get-user-response)
          actual-user (:result body)
          attributes [:name :email :nickname]]
      (is (= 200 (:status get-user-response)))
      (is (= (select-keys expected-user attributes) (select-keys actual-user attributes))))))

(deftest get-non-existing-user-by-id
  (testing "Get a non existing user returns failure"
    (let [get-user-response (client/get (resource (str "user/" (random-uuid))))
          body (body-as-json get-user-response)
          actual-user (:result body)]
      (is (= 200 (:status get-user-response)))
      (is (= {} actual-user)))))

;(deftest like-existing-tweet
;  (testing "Like an existing tweet"
;    (let [user (post-json (resource "user") (new-user))
;          user-id (get-in (body-as-json user) [:result :id])
;          tweet (post-json (resource "tweet") (new-tweet user-id))
;          tweet-id (get-in (body-as-json tweet) [:result :id])
;          updated-tweet (client/patch (resource "tweet/like") {:query-params {:tweet-id tweet-id}})
;          body (body-as-json updated-tweet)
;          result (:result body)]
;      (is (= "success" (:status body)))
;      (is (= 1 (:likes result))))))