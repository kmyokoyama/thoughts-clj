(ns twitter-clj.adapter.rest.rest-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [twitter-clj.application.config :refer [system-config]]
            [twitter-clj.application.test-util :refer :all]
            [twitter-clj.adapter.repository.in-mem :refer [make-in-mem-storage]]
            [twitter-clj.application.service :refer [make-service]]
            [twitter-clj.adapter.rest.component :refer [make-http-controller]]))

(def ^:const port (get-in system-config [:http :port]))
(def ^:const url (str "http://localhost:" port))

(def resource (partial resource-path url))

(defn- test-system
  [system-config]
  (component/system-map
    :repository (make-in-mem-storage)
    :service (component/using
               (make-service)
               [:repository])
    :controller (component/using
                  (make-http-controller (:http system-config))
                  [:service])))

(defn start-test-system!
  [system-config]
  (component/start (test-system system-config)))

(defn stop-test-system! [system]
  (component/stop system))

(use-fixtures :each (fn [f]
                      (let [system (start-test-system! system-config)]
                        (f)
                        (stop-test-system! system))))

(deftest add-single-user
  (testing "Add a single user"
    (let [response (post-json (resource "user") (random-user))]
      (is (= "success" (:status (body-as-json response))))
      (is (= 201 (:status response)))))) ;; HTTP 201 Created.

(deftest add-duplicate-user-email
  (testing "Add a single user with duplicate user email returns a failure"
    (let [first-user (random-user)
          first-user-email (:email first-user)]
      (post-json (resource "user") first-user)
      (let [second-user {:name (random-fullname) :email first-user-email :username (random-username)}
            [response body result] (parse-response (post-json (resource "user") second-user))]
        (is (= "failure" (:status body)))
        (is (= 200 (:status response)))
        (is (= "user" (:subject result)))
        (is (= "email" (get-in result [:context :attribute])))
        (is (= (clojure.string/lower-case first-user-email) (get-in result [:context :email])))))))

(deftest add-duplicate-username
  (testing "Add a single user with duplicate username returns a failure"
    (let [first-user (random-user)
          first-username (:username first-user)]
      (post-json (resource "user") first-user)
      (let [second-user {:name (random-fullname) :email (random-email) :username first-username}
            [response body result] (parse-response (post-json (resource "user") second-user))]
        (is (= "failure" (:status body)))
        (is (= 200 (:status response)))
        (is (= "user" (:subject result)))
        (is (= "username" (get-in result [:context :attribute])))
        (is (= (clojure.string/lower-case first-username) (get-in result [:context :username])))))))

(deftest add-single-tweet
  (testing "Add a single tweet"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          text "My first tweet"
          [response body result] (parse-response (post-json (resource "tweet") (random-tweet user-id text)))]
      (is (= "success" (:status body)))
      (is (= 201 (:status response))) ;; HTTP 201 Created.
      (is (= user-id (:user-id result)))
      (is (= text (:text result)))
      (is (= 0 (:likes result) (:retweets result) (:replies result))))))

(deftest get-tweets-from-user
  (testing "Get two tweets from the same user"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          first-tweet (post-json (resource "tweet") (random-tweet user-id))
          second-tweet (post-json (resource "tweet") (random-tweet user-id))
          first-tweet-id (get-in (body-as-json first-tweet) [:result :id])
          second-tweet-id (get-in (body-as-json second-tweet) [:result :id])
          [response body result] (parse-response (client/get (resource "tweet") {:query-params {:user-id user-id}}))]
      (is (= "success" (:status body)))
      (is (= 200 (:status response))) ;; HTTP 200 OK.
      (is (= 2 (count result)))
      (is (= #{first-tweet-id second-tweet-id} (into #{} (map :id result)))))))

(deftest get-empty-tweets
  (testing "Returns no tweet if user has not tweet yet"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])]
      ;; No tweet.
      (let [[response body result] (parse-response (client/get (resource "tweet") {:query-params {:user-id user-id}}))]
        (is (= "success" (:status body)))
        (is (= 200 (:status response))) ;; HTTP 200 OK.
        (is (= 0 (count result)))))))

(deftest get-user-by-id
  (testing "Get an existing user returns successfully"
    (let [expected-user (random-user)
          create-user-response (post-json (resource "user") expected-user)
          user-id (get-in (body-as-json create-user-response) [:result :id])
          [response _body result] (parse-response (client/get (resource (str "user/" user-id))))
          attributes [:name :email :username]]
      (is (= 200 (:status response)))
      (is (= (let [expected (select-keys expected-user attributes)]
               (zipmap (keys expected) (map clojure.string/lower-case (vals expected))))
             (select-keys result attributes))))))

(deftest get-user-by-missing-id
  (testing "Get a missing user returns failure"
    (let [user-id (random-uuid)
          [response body result] (parse-response (client/get (resource (str "user/" user-id))))]
      (is (= 200 (:status response)))
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "user" (:subject result)))
      (is (= (str user-id) (get-in result [:context :user-id]))))))

(deftest get-tweet-by-id
  (testing "Get an existing tweet returns successfully"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          expected-tweet (random-tweet user-id)
          create-tweet-response (post-json (resource "tweet") expected-tweet)
          tweet-id (get-in (body-as-json create-tweet-response) [:result :id])
          [response _body result] (parse-response (client/get (resource (str "tweet/" tweet-id))))
          zeroed-attributes [:likes :retweets :replies]]
      (is (= 200 (:status response)))
      (is (= (str (:user-id expected-tweet)) (:user-id result)))
      (is (= (:text expected-tweet) (:text result)))
      (is (every? zero? (vals (select-keys expected-tweet zeroed-attributes)))))))

(deftest get-tweet-by-missing-id
  (testing "Get a missing tweet returns failure"
    (let [tweet-id (random-uuid)
          [response body result] (parse-response (client/get (resource (str "tweet/" tweet-id))))]
      (is (= 200 (:status response)))
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest like-tweet
  (testing "Like an existing tweet"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (random-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          [response body result] (parse-response (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "like" :user-id user-id}))]
      (is (= 200 (:status response)))
      (is (= "success" (:status body)))
      (is (= tweet-id (:id result)))
      (is (= 1 (:likes result))))))

(deftest like-tweet-twice
  (testing "Like an existing tweet twice does not have any effect"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (random-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          _ (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "like" :user-id user-id})
          [response body result] (parse-response (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "like" :user-id user-id}))]
      (is (= 200 (:status response)))
      (is (= "failure" (:status body)))
      (is (= "invalid action" (:type result)))
      (is (= "like" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id])))
      (is (= (str user-id) (get-in result [:context :user-id]))))))

(deftest like-tweet-by-missing-id
  (testing "Like a missing tweet returns failure"
    (let [tweet-id (random-uuid)
          [response body result] (parse-response (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "like" :user-id (random-uuid)}))]
      (is (= 200 (:status response)))
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest unlike-tweet-with-positive-likes
  (testing "Unlike an existing tweet with positive likes"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (random-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          _ (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "like" :user-id user-id})
          [response body result] (parse-response (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "unlike" :user-id user-id}))]
      (is (= 200 (:status response)))
      (is (= "success" (:status body)))
      (is (= tweet-id (:id result)))
      (is (= 0 (:likes result))))))

(deftest unlike-tweet-with-zero-likes
  (testing "Unlike an existing tweet with positive likes"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (random-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          [response body result] (parse-response (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "unlike" :user-id user-id}))]
      (is (= 200 (:status response)))
      (is (= "failure" (:status body)))
      (is (= "invalid action" (:type result)))
      (is (= "unlike" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id])))
      (is (= (str user-id) (get-in result [:context :user-id]))))))


(deftest unlike-tweet-with-another-user
  (testing "Unlike an existing tweet with another user does not have any effect"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          other-user (post-json (resource "user") (random-user))
          other-user-id (get-in (body-as-json other-user) [:result :id])
          tweet (post-json (resource "tweet") (random-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          _ (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "like" :user-id user-id})
          [response body result] (parse-response (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "unlike" :user-id other-user-id}))]
      (is (= 200 (:status response)))
      (is (= "failure" (:status body)))
      (is (= "invalid action" (:type result)))
      (is (= "unlike" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id])))
      (is (= (str other-user-id) (get-in result [:context :user-id]))))))

(deftest unlike-tweet-with-missing-id
  (testing "Unlike a missing tweet returns failure"
    (let [tweet-id (random-uuid)
          [response body result] (parse-response (post-json (resource (str "tweet/" tweet-id "/react")) {:reaction "like" :user-id (random-uuid)}))]
      (is (= 200 (:status response)))
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest get-empty-retweets
  (testing "Get retweets from tweet not retweeted yet returns an empty list"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (random-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          [response body result] (parse-response (client/get (resource (str "tweet/" tweet-id "/retweets"))))]
      (is (= 200 (:status response)))
      (is (= "success" (:status body)))
      (is (empty? result)))))

(deftest get-non-empty-retweets
  (testing "Get retweets from a tweet already retweeted returns all replies"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (random-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])]
      (dotimes [_ 5] (post-json (resource (str "tweet/" tweet-id "/retweet")) {:user-id user-id}))
      (dotimes [_ 5] (post-json (resource (str "tweet/" tweet-id "/retweet-comment")) {:user-id user-id  :comment (random-text)}))
      (let [[response body result] (parse-response (client/get (resource (str "tweet/" tweet-id "/retweets"))))]
        (is (= 200 (:status response)))
        (is (= "success" (:status body)))
        (is (= 10 (count result)))))))

(deftest get-empty-replies
  (testing "Get replies from tweet not replied yet returns an empty list"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (random-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])
          [response body result] (parse-response (client/get (resource (str "tweet/" tweet-id "/replies"))))]
      (is (= 200 (:status response)))
      (is (= "success" (:status body)))
      (is (empty? result)))))

(deftest get-non-empty-replies
  (testing "Get retweets from a tweet already retweeted returns all replies"
    (let [user (post-json (resource "user") (random-user))
          user-id (get-in (body-as-json user) [:result :id])
          tweet (post-json (resource "tweet") (random-tweet user-id))
          tweet-id (get-in (body-as-json tweet) [:result :id])]
      (dotimes [_ 5] (post-json (resource (str "tweet/" tweet-id "/reply")) {:user-id user-id :text (random-text)}))
      (let [[response body result] (parse-response (client/get (resource (str "tweet/" tweet-id "/replies"))))]
        (is (= 200 (:status response)))
        (is (= "success" (:status body)))
        (is (= 5 (count result)))))))