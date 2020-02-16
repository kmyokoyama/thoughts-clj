(ns twitter-clj.rest-test
  (:require [twitter-clj.rest.handler :refer [handler]]
            [twitter-clj.operations :as app]
            [twitter-clj.test-utils :refer [resource-path body-as-json new-user new-tweet]]
            [clojure.test :refer :all]
            [clj-http.client :as client]
            [ring.server.standalone :as s]))

(def ^:const url "http://localhost:3000/")

(def server (atom nil))

(defn start-server [port]
  (println "Starting server...")
  (reset! server
          (s/serve handler {:port port :open-browser? false :auto-reload? false})))

(defn stop-server []
  (println "Stopping server.")
  (.stop @server)
  (reset! server nil)
  (app/shutdown))

(use-fixtures :each (fn [f] (start-server 3000) (f) (stop-server)))

(def resource (partial resource-path url))

(deftest add-single-user
  (testing "Add a single user"
    (let [response (client/get (resource "user") {:query-params (new-user "First User" "first@user.com" "first")})]
      (is (= "success" (:status (body-as-json response)))))))

(deftest get-users-successfully
  (testing "Get two users successfully"
    ;; Given.
    (client/get (resource "user") {:query-params (new-user "First User" "first@user.com" "first")})
    (client/get (resource "user") {:query-params (new-user "Second User" "second@user.com" "second")})
    ;; Then.
    (let [response (client/get (resource "users") {})]
      (is (= "success" (:status (body-as-json response))))
      (is (= 2 (count (:result (body-as-json response))))))))

(deftest add-single-tweet
  (testing "Add a single tweet"
    (let [user (client/get (resource "user") {:query-params (new-user "First User" "first@user.com" "first")})
          user-id (get-in (body-as-json user) [:result :id])
          text "My first tweet"
          response (client/get (resource "tweet") {:query-params (new-tweet user-id text)})
          body (body-as-json response)
          result (:result body)]
      (is (= "success" (:status body)))
      (is (= user-id (:user-id result)))
      (is (= text (:text result)))
      (is (= 0 (:likes result) (:retweets result) (:replies result))))))

(deftest get-tweets-successfully
  (testing "Get two tweets from the same user"
    (let [user (client/get (resource "user") {:query-params (new-user "First User" "first@user.com" "first")})
          user-id (get-in (body-as-json user) [:result :id])
          first-tweet (client/get (resource "tweet") {:query-params (new-tweet user-id "First tweet")})
          second-tweet (client/get (resource "tweet") {:query-params (new-tweet user-id "Second tweet")})
          first-tweet-id (get-in (body-as-json first-tweet) [:result :id])
          second-tweet-id (get-in (body-as-json second-tweet) [:result :id])
          response (client/get (resource "tweets") {:query-params {:user-id user-id}})
          body (body-as-json response)
          result (:result body)]
      (is (= "success" (:status body)))
      (is (= 2 (count result)))
      (is (= #{first-tweet-id second-tweet-id} (into #{} (map :id result)))))))

(deftest get-empty-tweets
  (testing "Get tweets returns no tweet if user has not tweet yet"
    (let [user (client/get (resource "user") {:query-params (new-user "First User" "first@user.com" "first")})
          user-id (get-in (body-as-json user) [:result :id])]
      ;; No tweet.
      (let [response (client/get (resource "tweets") {:query-params {:user-id user-id}})
            body (body-as-json response)
            result (:result body)]
        (is (= "success" (:status body)))
        (is (= 0 (count result)))))))
