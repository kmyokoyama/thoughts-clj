(ns twitter-clj.rest-test
  (:require [twitter-clj.rest.handler :refer [handler]]
            [clojure.test :refer :all]
            [ring.server.standalone :as s]
            [clj-http.client :as client]
            [clojure.data.json :as json]))

(def server (atom nil))

(defn start-server [port]
  (println "Starting server...")
  (reset! server
          (s/serve handler {:port port :open-browser? false :auto-reload? false})))

(defn stop-server []
  (println "Stopping server.")
  (.stop @server)
  (reset! server nil))

(def url "http://localhost:3000/")

(defn- resource
  [path]
  (str url "/" path))

(defn- body-as-json [{:keys [body]}]
  (if (string? body)
    (json/read-str body :key-fn keyword)
    body))

(defn- new-user
  [name email nickname]
  {:name name :email email :nickname nickname})

(use-fixtures :each (fn [f] (start-server 3000) (f) (stop-server)))

(deftest get-users-successfully
  (testing "Get two users successfully"
    ;; Given.
    (client/get (resource "user") {:query-params (new-user "First User" "first@user.com" "first")})
    (client/get (resource "user") {:query-params (new-user "Second User" "second@user.com" "second")})
    ;; Then.
    (let [response (client/get (resource "users") {})]
      (is (= "success" (:status (body-as-json response))))
      (is (= 2 (count (:result (body-as-json response))))))))

(deftest add-single-user
  (testing "Add a single user"
    (let [response (client/get (resource "user") {:query-params (new-user "First User" "first@user.com" "first")})]
      (is (= "success" (:status (body-as-json response)))))))