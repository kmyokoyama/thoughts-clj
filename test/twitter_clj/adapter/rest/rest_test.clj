(ns twitter-clj.adapter.rest.rest-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [twitter-clj.application.config :refer [system-config]]
            [twitter-clj.application.test-util :refer :all]
            [twitter-clj.adapter.repository.in-mem :refer [make-in-mem-storage]]
            [twitter-clj.application.service :refer [make-service]]
            [twitter-clj.adapter.rest.component :refer [make-http-controller]]
            [twitter-clj.adapter.rest.test-util :refer :all]))

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

(defn- start-test-system!
  [system-config]
  (component/start (test-system system-config)))

(defn- stop-test-system! [system]
  (component/stop system))

(use-fixtures :each (fn [f]
                      (let [system (start-test-system! system-config)]
                        (f)
                        (stop-test-system! system))))

(defn- create-user-and-login                                ;; TODO: Move it.
  ([]
   (let [user (random-user)
         password (:password user)
         user-id (-> (post-and-parse (resource "user") user) :result :id)
         token (-> (post-and-parse (resource "login") {:user-id user-id :password password}) :result :token)]
     {:user-id user-id :token token}))

  ([user]
   (let [password (:password user)
         user-id (-> (post-and-parse (resource "user") user) :result :id)
         token (-> (post-and-parse (resource "login") {:user-id user-id :password password}) :result :token)]
     {:user-id user-id :token token})))

;; Tests.

(deftest add-single-user
  (testing "Add a single user"
    (let [response (post (resource "user") (random-user))]
      (is (= "success" (:status (get-body response))))
      (is (= 201 (:status response))))))                    ;; HTTP 201 Created.

(deftest add-duplicate-user-email
  (testing "Add a single user with duplicate user email returns a failure"
    (let [first-user (random-user)
          first-user-email (:email first-user)]
      (post (resource "user") first-user)
      (let [second-user {:name (random-fullname) :email first-user-email :username (random-username)}
            {:keys [response body result]} (post-and-parse (resource "user") second-user)]
        (is (= "failure" (:status body)))
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is (= "user" (:subject result)))
        (is (= "email" (get-in result [:context :attribute])))
        (is (= (clojure.string/lower-case first-user-email) (get-in result [:context :email])))))))

(deftest add-duplicate-username
  (testing "Add a single user with duplicate username returns a failure"
    (let [first-user (random-user)
          first-username (:username first-user)]
      (post (resource "user") first-user)
      (let [second-user {:name (random-fullname) :email (random-email) :username first-username}
            {:keys [response body result]} (post-and-parse (resource "user") second-user)]
        (is (= "failure" (:status body)))
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is (= "user" (:subject result)))
        (is (= "username" (get-in result [:context :attribute])))
        (is (= (clojure.string/lower-case first-username) (get-in result [:context :username])))))))

(deftest login-with-existing-user
  (testing "Login returns successfully when user exists"
    (let [user (random-user)
          password (:password user)
          user-id (-> (post-and-parse (resource "user") user) :result :id)
          {:keys [response body result]} (post-and-parse (resource "login") {:user-id user-id :password password})]
      (is (= "success" (:status body)))
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (and (string? (:token result)) ((complement clojure.string/blank?) (:token result)))))))

(deftest login-with-existing-user-twice
  (testing "Login fails when user is already logged in"
    (let [user-id (random-uuid)
          password (random-password)]
      (post-and-parse (resource "login") {:user-id user-id :password password}) ;; Log first time.
      (let [{:keys [response body result]} (post-and-parse (resource "login") {:user-id user-id :password password})]
        (is (= "failure" (:status body)))
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is ((complement contains?) result :token))))))

(deftest login-with-missing-user
  (testing "Login fails when user does not exist"
    (let [user-id (random-uuid)
          password (random-password)
          {:keys [response body result]} (post-and-parse (resource "login") {:user-id user-id :password password})]
      (is (= "failure" (:status body)))
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is ((complement contains?) result :token)))))

(deftest logout-with-logged-user
  (testing "Logout returns successfully when user is already logged in"
    (let [{:keys [token]} (create-user-and-login)
          {:keys [response body]} (post-and-parse (resource "logout") token {})]
      (is (= "success" (:status body)))
      (is (= 200 (:status response))))))

(deftest logout-with-logged-out-user
  (testing "Logout fails when user is not logged in yet"
    (let [{:keys [response body]} (post-and-parse (resource "logout") nil {})]
      (is (= "failure" (:status body)))
      (is (= 401 (:status response))))))                    ;; HTTP 401 Unauthorized.

(deftest add-single-tweet
  (testing "Add a single tweet"
    (let [{:keys [user-id token]} (create-user-and-login)
          text (random-text)
          {:keys [response body result]} (post-and-parse (resource "tweet") token (random-tweet text))]
      (is (= "success" (:status body)))
      (is (= 201 (:status response)))                       ;; HTTP 201 Created.
      (is (= user-id (:user-id result)))
      (is (= text (:text result)))
      (is (= 0 (:likes result) (:retweets result) (:replies result))))))

(deftest get-tweets-from-user
  (testing "Get two tweets from the same user"
    (let [{:keys [user-id token]} (create-user-and-login)
          first-tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          second-tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (get-and-parse (resource (str "user/" user-id "/tweets")) token)]
      (is (= "success" (:status body)))
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= 2 (count result)))
      (is (= #{first-tweet-id second-tweet-id} (into #{} (map :id result)))))))

(deftest get-empty-tweets
  (testing "Returns no tweet if user has not tweet yet"
    (let [{:keys [user-id token]} (create-user-and-login)]
      ;; No tweet.
      (let [{:keys [response body result]} (get-and-parse (resource (str "user/" user-id "/tweets")) token)]
        (is (= "success" (:status body)))
        (is (= 200 (:status response)))                     ;; HTTP 200 OK.
        (is (empty? result))))))

(deftest get-user-by-id
  (testing "Get an existing user returns successfully"
    (let [expected-user (random-user)
          {:keys [user-id token]} (create-user-and-login expected-user)
          {:keys [response result]} (get-and-parse (resource (str "user/" user-id)) token)
          attributes [:name :email :username]]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= (let [expected (select-keys expected-user attributes)]
               (zipmap (keys expected) (map clojure.string/lower-case (vals expected))))
             (select-keys result attributes))))))

(deftest get-user-by-missing-id
  (testing "Get a missing user returns failure"
    (let [{:keys [token]} (create-user-and-login)
          user-id (random-uuid)
          {:keys [response body result]} (get-and-parse (resource (str "user/" user-id)) token)]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "user" (:subject result)))
      (is (= (str user-id) (get-in result [:context :user-id]))))))

(deftest get-tweet-by-id
  (testing "Get an existing tweet returns successfully"
    (let [{:keys [user-id token]} (create-user-and-login)
          expected-tweet (random-tweet)
          tweet-id (-> (post-and-parse (resource "tweet") token expected-tweet) :result :id)
          {:keys [response result]} (get-and-parse (resource (str "tweet/" tweet-id)) token)
          zeroed-attributes [:likes :retweets :replies]]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= user-id (:user-id result)))
      (is (= (:text expected-tweet) (:text result)))
      (is (every? zero? (vals (select-keys expected-tweet zeroed-attributes)))))))

(deftest get-tweet-by-missing-id
  (testing "Get a missing tweet returns failure"
    (let [{:keys [token]} (create-user-and-login)
          tweet-id (random-uuid)
          {:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id)) token)]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest like-tweet
  (testing "Like an existing tweet"
    (let [{:keys [token]} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/react"))
                                                         token
                                                         {:reaction "like"})]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (= tweet-id (:id result)))
      (is (= 1 (:likes result))))))

(deftest like-tweet-twice
  (testing "Like an existing tweet twice does not have any effect"
    (let [{:keys [user-id token]} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)]
      (post (resource (str "tweet/" tweet-id "/react")) token {:reaction "like" :user-id user-id})
      (let [{:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/react"))
                                                           token
                                                           {:reaction "like"})]
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is (= "failure" (:status body)))
        (is (= "invalid action" (:type result)))
        (is (= "like" (:subject result)))
        (is (= (str tweet-id) (get-in result [:context :tweet-id])))
        (is (= (str user-id) (get-in result [:context :user-id])))))))

(deftest like-tweet-by-missing-id
  (testing "Like a missing tweet returns failure"
    (let [{:keys [user-id token]} (create-user-and-login)
          tweet-id (random-uuid)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/react"))
                                                         token
                                                         {:reaction "like"})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest unlike-tweet-with-positive-likes
  (testing "Unlike an existing tweet with positive likes"
    (let [{:keys [user-id token]} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)]
      (post (resource (str "tweet/" tweet-id "/react")) token {:reaction "like" :user-id user-id})
      (let [{:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/react"))
                                                           token
                                                           {:reaction "unlike"})]
        (is (= 200 (:status response)))                     ;; HTTP 200 OK.
        (is (= "success" (:status body)))
        (is (= tweet-id (:id result)))
        (is (= 0 (:likes result)))))))

(deftest unlike-tweet-with-zero-likes
  (testing "Unlike an existing tweet with positive likes"
    (let [{:keys [user-id token]} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/react"))
                                                         token
                                                         {:reaction "unlike"})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "invalid action" (:type result)))
      (is (= "unlike" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id])))
      (is (= (str user-id) (get-in result [:context :user-id]))))))

(deftest unlike-tweet-with-another-user
  (testing "Unlike an existing tweet with another user does not have any effect"
    (let [{:keys [token]} (create-user-and-login)
          {other-user-id :user-id other-token :token} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)]
      (post (resource (str "tweet/" tweet-id "/react")) token {:reaction "like"})
      (let [{:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/react"))
                                                           other-token
                                                           {:reaction "unlike"})]
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is (= "failure" (:status body)))
        (is (= "invalid action" (:type result)))
        (is (= "unlike" (:subject result)))
        (is (= (str tweet-id) (get-in result [:context :tweet-id])))
        (is (= (str other-user-id) (get-in result [:context :user-id])))))))

(deftest unlike-tweet-with-missing-id
  (testing "Unlike a missing tweet returns failure"
    (let [{:keys [token]} (create-user-and-login)
          tweet-id (random-uuid)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/react"))
                                                         token
                                                         {:reaction "like"})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest add-reply
  (testing "Add new reply to existing tweet returns successfully"
    (let [{:keys [user-id token]} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          reply-text (random-text)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/reply")) token {:text reply-text})]
      (is (= "success" (:status body)))
      (is (= 201 (:status response)))                       ;; HTTP 201 Created.
      (is (= user-id (:user-id result)))
      (is (= reply-text (:text result)))
      (is (= 0 (:likes result) (:retweets result) (:replies result))))))

(deftest add-reply-to-missing-tweet
  (testing "Add new reply to missing tweet fails"
    (let [{:keys [token]} (create-user-and-login)
          tweet-id (random-uuid)
          reply-text (random-text)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/reply")) token {:text reply-text})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest get-empty-retweets
  (testing "Get retweets from tweet not retweeted yet returns an empty list"
    (let [{:keys [token]} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id "/retweets")) token)]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (empty? result)))))

(deftest get-retweets
  (testing "Get retweets from a tweet already retweeted returns all replies"
    (let [{:keys [user-id token]} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet user-id)) :result :id)]
      (dotimes [_ 5] (post (resource (str "tweet/" tweet-id "/retweet")) token {:user-id user-id}))
      (dotimes [_ 5] (post (resource (str "tweet/" tweet-id "/retweet-comment")) token {:user-id user-id :comment (random-text)}))
      (let [{:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id "/retweets")) token)]
        (is (= 200 (:status response)))                     ;; HTTP 200 OK.
        (is (= "success" (:status body)))
        (is (= 10 (count result)))))))

(deftest get-empty-replies
  (testing "Get replies from tweet not replied yet returns an empty list"
    (let [{:keys [token]} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id "/replies")) token)]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (empty? result)))))

(deftest get-replies
  (testing "Get retweets from a tweet already retweeted returns all replies"
    (let [{:keys [user-id token]} (create-user-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)]
      (dotimes [_ 5] (post (resource (str "tweet/" tweet-id "/reply")) token {:user-id user-id :text (random-text)}))
      (let [{:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id "/replies")) token)]
        (is (= 200 (:status response)))                     ;; HTTP 200 OK.
        (is (= "success" (:status body)))
        (is (= 5 (count result)))))))