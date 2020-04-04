(ns twitter-clj.application.test-util
  (:require [clojure.test :refer :all]
            [faker.name :as name]
            [faker.internet :as internet]
            [faker.lorem :as lorem]
            [clojure.string :refer [join]]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [twitter-clj.adapter.rest.config :refer [path-prefix]])
  (:import [java.util UUID]
           [java.time ZonedDateTime]))

;; REST API.

;; TODO: Implement get-json.

(defn post-json
  [url body]
  (client/post url {:form-params body :content-type :json}))

(defn resource-path
  [url path]
  (join "/" (list url (path-prefix path))))

(defn body-as-json [{:keys [body]}]
  (if (string? body)
    (json/read-str body :key-fn keyword)
    body))

(defn parse-response
  [response]
  (let [body (body-as-json response)]
    [response body (:result body)]))

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

(defn now [] (ZonedDateTime/now))

;; Random full entities.

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