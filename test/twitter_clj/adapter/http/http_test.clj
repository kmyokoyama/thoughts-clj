(ns twitter-clj.adapter.http.http-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [twitter-clj.application.test-util :refer :all]
            [twitter-clj.adapter.repository.in-mem :refer [make-in-mem-repository]]
            [twitter-clj.adapter.repository.datomic :refer [delete-database
                                                            make-datomic-repository
                                                            load-schema]]
            [twitter-clj.application.config :refer [datomic-uri http-host http-port]]
            [twitter-clj.application.service :refer [make-service]]
            [twitter-clj.adapter.http.component :refer [make-http-controller]]
            [twitter-clj.adapter.http.test-util :refer :all])
  (:import (java.time ZonedDateTime)
           (java.util Date)))

(def ^:private ^:const url (str "http://" http-host ":" http-port))

(def ^:private resource (partial resource-path url))

(defn- test-system-map
  []
  (component/system-map
    :repository (make-datomic-repository datomic-uri)
    :service (component/using
               (make-service)
               [:repository])
    :controller (component/using
                  (make-http-controller http-host http-port)
                  [:service])))

(defn- start-test-system-with-in-mem
  []
  (component/start (test-system-map)))

(defn- stop-test-system-with-in-mem
  [sys]
  (component/stop sys))

(defn- start-test-system-with-datomic
  []
  (let [sys (component/start (test-system-map))
        conn (get-in sys [:repository :conn])]
    (load-schema conn "schema.edn")
    sys))

(defn- stop-test-system-with-datomic
  [system]
  (delete-database datomic-uri)
  (component/stop system))

(use-fixtures :each (fn [f]
                      (let [sys (start-test-system-with-datomic)]
                        (f)
                        (stop-test-system-with-datomic sys))))

(defn signup
  [user]
  (let [password (:password user)
        user-id (-> (post-and-parse (resource "signup") user) :result :id)]
    {:user-id user-id :password password}))

(defn login
  [{:keys [user-id password]}]
  (let [token (-> (post-and-parse (resource "login") {:user-id user-id :password password}) :result :token)]
    {:user-id user-id :token token}))

(defn signup-and-login
  ([]
   (signup-and-login (random-user)))

  ([user]
   (-> user
       (signup)
       (login))))

;; Tests.

(deftest test-signup
  (testing "Sign up with a single user"
    (let [response (post (resource "signup") (random-user))]
      (is (= "success" (:status (get-body response))))
      (is (= 201 (:status response)))))                     ;; HTTP 201 Created.

  (testing "Sign up with duplicate email returns a failure"
    (let [first-user (random-user)
          first-user-email (:email first-user)]
      (post (resource "signup") first-user)
      (let [second-user {:name (random-fullname) :email first-user-email :username (random-username)}
            {:keys [response body result]} (post-and-parse (resource "signup") second-user)]
        (is (= "failure" (:status body)))
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is (= "user" (:subject result)))
        (is (= "email" (get-in result [:context :attribute])))
        (is (= (clojure.string/lower-case first-user-email) (get-in result [:context :email]))))))

  (testing "Sign up with duplicate username returns a failure"
    (let [first-user (random-user)
          first-username (:username first-user)]
      (post (resource "signup") first-user)
      (let [second-user {:name (random-fullname) :email (random-email) :username first-username}
            {:keys [response body result]} (post-and-parse (resource "signup") second-user)]
        (is (= "failure" (:status body)))
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is (= "user" (:subject result)))
        (is (= "username" (get-in result [:context :attribute])))
        (is (= (clojure.string/lower-case first-username) (get-in result [:context :username])))))))

(deftest test-login
  (testing "Login returns success when user exists"
    (let [user (random-user)
          password (:password user)
          user-id (-> (signup user) :user-id)
          {:keys [response body result]} (post-and-parse (resource "login") {:user-id user-id :password password})]
      (is (= "success" (:status body)))
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (and (string? (:token result)) ((complement clojure.string/blank?) (:token result))))))

  (testing "Login fails when user is already logged in"
    (let [user-id (random-uuid)
          password (random-password)]
      (post-and-parse (resource "login") {:user-id user-id :password password}) ;; Log first time.
      (let [{:keys [response body result]} (post-and-parse (resource "login") {:user-id user-id :password password})]
        (is (= "failure" (:status body)))
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is ((complement contains?) result :token)))))

  (testing "Login fails when user does not exist"
    (let [user-id (random-uuid)
          password (random-password)
          {:keys [response body result]} (post-and-parse (resource "login") {:user-id user-id :password password})]
      (is (= "failure" (:status body)))
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is ((complement contains?) result :token)))))

(deftest logout
  (testing "Logout returns success when user is already logged in"
    (let [{:keys [token]} (signup-and-login)
          {:keys [response body]} (post-and-parse (resource "logout") token {})]
      (is (= "success" (:status body)))
      (is (= 200 (:status response)))))

  (testing "Logout fails when user is not logged in yet"
    (let [{:keys [response body]} (post-and-parse (resource "logout") nil {})]
      (is (= "failure" (:status body)))
      (is (= 401 (:status response))))))                    ;; HTTP 401 Unauthorized.

(deftest test-tweet
  (testing "Add a single tweet"
    (let [{:keys [user-id token]} (signup-and-login)
          text (random-text)
          {:keys [response body result]} (post-and-parse (resource "tweet") token (random-tweet text))]
      (is (= "success" (:status body)))
      (is (= 201 (:status response)))                       ;; HTTP 201 Created.
      (is (= user-id (:user-id result)))
      (is (= text (:text result)))
      (is (= 0 (:likes result) (:retweets result) (:replies result))))))

(deftest test-get-tweets-from-user
  (testing "Get two tweets from the same user"
    (let [{:keys [user-id token]} (signup-and-login)
          first-tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          second-tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (get-and-parse (resource (str "user/" user-id "/tweets")) token)]
      (is (= "success" (:status body)))
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= 2 (count result)))
      (is (= #{first-tweet-id second-tweet-id} (into #{} (map :id result))))))

  (testing "Returns no tweet if user has not tweet yet"
    (let [{:keys [user-id token]} (signup-and-login)]
      ;; No tweet.
      (let [{:keys [response body result]} (get-and-parse (resource (str "user/" user-id "/tweets")) token)]
        (is (= "success" (:status body)))
        (is (= 200 (:status response)))                     ;; HTTP 200 OK.
        (is (empty? result))))))

(deftest test-get-user-by-id
  (testing "Get an existing user returns successfully"
    (let [expected-user (random-user)
          {:keys [user-id token]} (signup-and-login expected-user)
          {:keys [response result]} (get-and-parse (resource (str "user/" user-id)) token)
          attributes [:name :email :username]]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= (let [expected (select-keys expected-user attributes)]
               (zipmap (keys expected) (map clojure.string/lower-case (vals expected))))
             (select-keys result attributes)))))

  (testing "Get a missing user returns failure"
    (let [{:keys [token]} (signup-and-login)
          user-id (random-uuid)
          {:keys [response body result]} (get-and-parse (resource (str "user/" user-id)) token)]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "user" (:subject result)))
      (is (= (str user-id) (get-in result [:context :user-id]))))))

(deftest test-get-tweet-by-id
  (testing "Get an existing tweet returns successfully"
    (let [{:keys [user-id token]} (signup-and-login)
          expected-tweet (random-tweet)
          tweet-id (-> (post-and-parse (resource "tweet") token expected-tweet) :result :id)
          {:keys [response result]} (get-and-parse (resource (str "tweet/" tweet-id)) token)
          zeroed-attributes [:likes :retweets :replies]]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= user-id (:user-id result)))
      (is (= (:text expected-tweet) (:text result)))
      (is (every? zero? (vals (select-keys expected-tweet zeroed-attributes))))))

  (testing "Get a missing tweet returns failure"
    (let [{:keys [token]} (signup-and-login)
          tweet-id (random-uuid)
          {:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id)) token)]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest test-like
  (testing "Like an existing tweet"
    (let [{:keys [token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/like"))
                                                         token
                                                         {})]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (= tweet-id (:id result)))
      (is (= 1 (:likes result)))))

  (testing "Like the same tweet twice does not have any effect"
    (let [{:keys [user-id token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)]
      (post (resource (str "tweet/" tweet-id "/like")) token {})
      (let [{:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/like"))
                                                           token
                                                           {})]
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is (= "failure" (:status body)))
        (is (= "invalid action" (:type result)))
        (is (= "like" (:subject result)))
        (is (= (str tweet-id) (get-in result [:context :tweet-id])))
        (is (= (str user-id) (get-in result [:context :user-id]))))))

  (testing "Like a missing tweet returns failure"
    (let [{:keys [user-id token]} (signup-and-login)
          tweet-id (random-uuid)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/like"))
                                                         token
                                                         {})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest test-unlike
  (testing "Unlike an existing tweet previously liked"
    (let [{:keys [user-id token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)]
      (post (resource (str "tweet/" tweet-id "/like")) token {})
      (let [{:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/unlike"))
                                                           token
                                                           {})]
        (is (= 200 (:status response)))                     ;; HTTP 200 OK.
        (is (= "success" (:status body)))
        (is (= tweet-id (:id result)))
        (is (= 0 (:likes result))))))

  (testing "Unlike an existing tweet not previously liked"
    (let [{:keys [user-id token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/unlike"))
                                                         token
                                                         {})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "invalid action" (:type result)))
      (is (= "unlike" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id])))
      (is (= (str user-id) (get-in result [:context :user-id])))))

  (testing "Unlike an existing tweet with another user does not have any effect"
    (let [{:keys [token]} (signup-and-login)
          {other-user-id :user-id other-token :token} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)]
      (post (resource (str "tweet/" tweet-id "/like")) token {})
      (let [{:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/unlike"))
                                                           other-token
                                                           {})]
        (is (= 400 (:status response)))                     ;; HTTP 400 Bad Request.
        (is (= "failure" (:status body)))
        (is (= "invalid action" (:type result)))
        (is (= "unlike" (:subject result)))
        (is (= (str tweet-id) (get-in result [:context :tweet-id])))
        (is (= (str other-user-id) (get-in result [:context :user-id]))))))

  (testing "Unlike a missing tweet returns failure"
    (let [{:keys [token]} (signup-and-login)
          tweet-id (random-uuid)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/unlike"))
                                                         token
                                                         {})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest test-add-reply
  (testing "Add new reply to existing tweet returns success"
    (let [{:keys [user-id token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          reply-text (random-text)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/reply")) token {:text reply-text})]
      (is (= "success" (:status body)))
      (is (= 201 (:status response)))                       ;; HTTP 201 Created.
      (is (= user-id (:user-id result)))
      (is (= reply-text (:text result)))
      (is (= 0 (:likes result) (:retweets result) (:replies result)))))

  (testing "Add new reply to missing tweet fails"
    (let [{:keys [token]} (signup-and-login)
          tweet-id (random-uuid)
          reply-text (random-text)
          {:keys [response body result]} (post-and-parse (resource (str "tweet/" tweet-id "/reply")) token {:text reply-text})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "tweet" (:subject result)))
      (is (= (str tweet-id) (get-in result [:context :tweet-id]))))))

(deftest test-get-retweet-by-id
  (testing "Get retweets from tweet not retweeted yet returns an empty list"
    (let [{:keys [token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          retweet-id (-> (post-and-parse (resource (str "tweet/" tweet-id "/retweet")) token {}) :result :id)
          {:keys [response body result]} (get-and-parse (resource (str "retweet/" retweet-id)) token)]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (= retweet-id (:id result)))
      (is (= tweet-id (:source-tweet-id result))))))

(deftest test-get-retweets
  (testing "Get retweets from a tweet already retweeted returns all replies"
    (let [{:keys [user-id token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet user-id)) :result :id)]
      (dotimes [_ 5] (post (resource (str "tweet/" tweet-id "/retweet")) token {}))
      (dotimes [_ 5] (post (resource (str "tweet/" tweet-id "/retweet-comment")) token {:comment (random-text)}))
      (let [{:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id "/retweets")) token)]
        (is (= 200 (:status response)))                     ;; HTTP 200 OK.
        (is (= "success" (:status body)))
        (is (= 10 (count result))))))

  (testing "Get retweets from tweet not retweeted yet returns an empty list"
    (let [{:keys [token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id "/retweets")) token)]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (empty? result)))))

(deftest test-get-replies
  (testing "Get retweets from a tweet already retweeted returns all replies"
    (let [{:keys [user-id token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)]
      (dotimes [_ 5] (post (resource (str "tweet/" tweet-id "/reply")) token {:user-id user-id :text (random-text)}))
      (let [{:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id "/replies")) token)]
        (is (= 200 (:status response)))                     ;; HTTP 200 OK.
        (is (= "success" (:status body)))
        (is (= 5 (count result))))))

  (testing "Get replies from tweet not replied yet returns an empty list"
    (let [{:keys [token]} (signup-and-login)
          tweet-id (-> (post-and-parse (resource "tweet") token (random-tweet)) :result :id)
          {:keys [response body result]} (get-and-parse (resource (str "tweet/" tweet-id "/replies")) token)]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (empty? result)))))

(deftest test-follow
  (testing "User follows another user"
    (let [{follower-id :user-id follower-token :token} (signup-and-login)
          {followed-id :user-id} (signup-and-login)
          {:keys [response body]} (post-and-parse (resource (str "user/" followed-id "/follow")) follower-token {})
          followed-result (:result body)
          follower-result (-> (get-and-parse (resource (str "user/" follower-id)) follower-token {}) :result)]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (= 1 (:followers followed-result)))
      (is (= 1 (:following follower-result)))))

  (testing "Follow a missing user returns failure"
    (let [{:keys [token]} (signup-and-login)
          random-followed-id (random-uuid)
          {:keys [response body result]} (post-and-parse (resource (str "user/" random-followed-id "/follow")) token {})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "user" (:subject result)))
      (is (= (str random-followed-id) (get-in result [:context :user-id])))))

  (testing "Follow the same user twice returns failure"
    (let [{:keys [user-id token]} (signup-and-login)
          {followed-id :user-id} (signup-and-login)
          _ (post-and-parse (resource (str "user/" followed-id "/follow")) token {})
          {:keys [response body result]} (post-and-parse (resource (str "user/" followed-id "/follow")) token {})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "invalid action" (:type result)))
      (is (= "follow" (:subject result)))
      (is (= (str user-id) (get-in result [:context :follower-id])))
      (is (= (str followed-id) (get-in result [:context :followed-id]))))))

(deftest test-unfollow
  (testing "User unfollows an user she/he follows"
    (let [{follower-id :user-id follower-token :token} (signup-and-login)
          {followed-id :user-id} (signup-and-login)
          _ (post-and-parse (resource (str "user/" followed-id "/follow")) follower-token {})
          {:keys [response body]} (post-and-parse (resource (str "user/" followed-id "/unfollow")) follower-token {})
          unfollowed-result (:result body)
          follower-result (-> (get-and-parse (resource (str "user/" follower-id)) follower-token {}) :result)]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (= 0 (:followers unfollowed-result)))
      (is (= 0 (:following follower-result)))))

  (testing "Unfollow a missing user returns failure"
    (let [{:keys [token]} (signup-and-login)
          random-followed-id (random-uuid)
          {:keys [response body result]} (post-and-parse (resource (str "user/" random-followed-id "/unfollow")) token {})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "resource not found" (:type result)))
      (is (= "user" (:subject result)))
      (is (= (str random-followed-id) (get-in result [:context :user-id])))))

  (testing "Unfollow an user that is not currently followed"
    (let [{:keys [user-id token]} (signup-and-login)
          {followed-id :user-id} (signup-and-login)
          {:keys [response body result]} (post-and-parse (resource (str "user/" followed-id "/unfollow")) token {})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "invalid action" (:type result)))
      (is (= "unfollow" (:subject result)))
      (is (= (str user-id) (get-in result [:context :follower-id])))
      (is (= (str followed-id) (get-in result [:context :followed-id])))))

  (testing "Unfollow the same user twice returns failure"
    (let [{:keys [user-id token]} (signup-and-login)
          {followed-id :user-id} (signup-and-login)
          _ (post-and-parse (resource (str "user/" followed-id "/follow")) token {})
          _ (post-and-parse (resource (str "user/" followed-id "/unfollow")) token {}) ;; First unfollow.
          {:keys [response body result]} (post-and-parse (resource (str "user/" followed-id "/unfollow")) token {})]
      (is (= 400 (:status response)))                       ;; HTTP 400 Bad Request.
      (is (= "failure" (:status body)))
      (is (= "invalid action" (:type result)))
      (is (= "unfollow" (:subject result)))
      (is (= (str user-id) (get-in result [:context :follower-id])))
      (is (= (str followed-id) (get-in result [:context :followed-id]))))))

(deftest test-get-user-following
  (testing "Get following list of an user"
    (let [{follower-id :user-id follower-token :token} (signup-and-login)
          {followed-id :user-id} (signup-and-login)
          _ (post-and-parse (resource (str "user/" followed-id "/follow")) follower-token {})
          {:keys [response body result]} (get-and-parse (resource (str "user/" follower-id "/following")) follower-token {})]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (= 1 (count result)))))

  (testing "Get following list of an user that does not follow anyone returns an empty list"
    (let [{:keys [user-id token]} (signup-and-login)
          {:keys [response body result]} (get-and-parse (resource (str "user/" user-id "/following")) token {})]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (empty? result)))))

(deftest test-get-user-followers
  (testing "Get followers list of an user"
    (let [{follower-token :token} (signup-and-login)
          {followed-id :user-id} (signup-and-login)
          _ (post-and-parse (resource (str "user/" followed-id "/follow")) follower-token {})
          {:keys [response body result]} (get-and-parse (resource (str "user/" followed-id "/followers")) follower-token {})]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (= 1 (count result)))))

  (testing "Get followers list of an user that is not followed by anyone returns an empty list"
    (let [{:keys [user-id token]} (signup-and-login)
          {:keys [response body result]} (get-and-parse (resource (str "user/" user-id "/followers")) token {})]
      (is (= 200 (:status response)))                       ;; HTTP 200 OK.
      (is (= "success" (:status body)))
      (is (empty? result)))))

(deftest test-get-feed
  (testing "Get feed of an user returns most recent tweets"
    (let [{first-user-id :user-id first-user-password :password} (signup (random-user))
          {second-user-id :user-id second-user-password :password} (signup (random-user))
          {third-user-id :user-id third-user-password :password} (signup (random-user))
          second-user-token (-> (login {:user-id second-user-id :password second-user-password}) :token)]
      (dotimes [_ 5] (post (resource "tweet") second-user-token (random-tweet)))

      (let [third-user-token (-> (login {:user-id third-user-id :password third-user-password}) :token)]
        (dotimes [_ 5] (post (resource "tweet") third-user-token (random-tweet)))

        (let [first-user-token (-> (login {:user-id first-user-id :password first-user-password}) :token)]
          (post (resource (str "user/" second-user-id "/follow")) first-user-token {})
          (post (resource (str "user/" third-user-id "/follow")) first-user-token {})

          (let [result (-> (get-and-parse (resource "feed") first-user-token {}) :result)]
            (is (= 10 (count result)))
            (is (->> result (map :publish-date) (map str->EpochSecond) (apply >=)))))))))