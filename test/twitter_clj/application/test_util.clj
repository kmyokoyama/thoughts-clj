(ns twitter-clj.application.test-util
  (:require [clojure.test :refer :all]
            [faker.name :as name]
            [faker.internet :as internet]
            [faker.lorem :as lorem]
            [clojure.string :refer [join]]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [twitter-clj.application.core :as core])
  (:import [java.util UUID]
           [java.time ZonedDateTime]))

;; Random data generators.

(defn random-fullname
  []
  (str (name/first-name) " " (name/last-name)))

(defn random-email
  []
  (internet/email))

(defn random-username
  []
  (internet/user-name))

(defn random-text
  []
  (join "\n" (take 2 (lorem/paragraphs))))

(defn random-uuid
  []
  (UUID/randomUUID))

(defn- random-tweet
  []
  (core/new-tweet (random-uuid) (random-text)))

(defn post-json
  [url body]
  (client/post url {:form-params body :content-type :json}))

(defn resource-path
  [url path]
  (str url "/" path))

(defn body-as-json [{:keys [body]}]
  (if (string? body)
    (json/read-str body :key-fn keyword)
    body))

(defn random-user
  []
  {:name (random-fullname) :email (random-email) :username (random-username)})

(defn random-tweet
  ([]
   (random-tweet (random-uuid)))

  ([user-id]
   {:user-id user-id :text (random-text)})

  ([user-id text]
   {:user-id user-id :text text}))

(defn now [] (ZonedDateTime/now))

;; Checkers.

(defn uuid-format?
  [str]
  (try (UUID/fromString str)
       true
       (catch IllegalArgumentException e false)))

;; Logging.

(defn highlight
  [& args]
  (println "=============================================")
  (apply println args)
  (println "============================================="))