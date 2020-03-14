(ns twitter-clj.adapter.rest.rest-test
  (:require [twitter-clj.test-utils :refer :all]
            [clojure.test :refer :all]
            [clj-http.client :as client]
            [com.stuartsierra.component :as component]
            [twitter-clj.adapter.rest.component :refer [make-http-controller]]
            [twitter-clj.adapter.storage.in-mem :refer [make-in-mem-storage]]
            [twitter-clj.application.app :refer [make-app]]))

;; TODO: Move to util.
(def ^:const port 3000)
(def ^:const url (str "http://localhost:" port "/"))
(def system-config {:server-config {:port port}})
(def resource (partial resource-path url))

(defn test-system
  [system-config]
  (component/system-map
    :storage (make-in-mem-storage)
    :app (component/using
           (make-app)
           [:storage])
    :controller (component/using
                  (make-http-controller (:server-config system-config))
                  [:app])))

(defn start-test-system [system-config]
  (component/start (test-system system-config)))

(defn stop-test-system [system]
  (component/stop system))

(use-fixtures :each (fn [f]
                      (let [system (start-test-system system-config)]
                        (f)
                        (stop-test-system system))))

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