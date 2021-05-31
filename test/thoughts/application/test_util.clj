(ns twitter-clj.application.test-util
  (:require [clojure.test :refer :all]
            [clojure.data.generators :as random]
            [faker.name :as name]
            [faker.internet :as internet]
            [faker.lorem :as lorem]
            [clojure.string :refer [join]]
            [clj-http.client :as client]
            [clojure.data.json :as json])
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

(defn random-password
  ([]
   (random-password 10))

  ([n]
   (->> (random/string) (take n) (apply str))))

(defn random-text
  []
  (join "\n" (take 2 (lorem/paragraphs))))

(defn random-uuid
  []
  (str (UUID/randomUUID)))

(defn now [] (ZonedDateTime/now))

;; Random full entities.

(defn random-user
  []
  {:name (random-fullname) :email (random-email) :username (random-username) :password (random-password)})

(defn random-tweet
  ([]
   (random-tweet (random-uuid)))

  ([text]
   {:text text}))

;; Checkers.

(defn uuid-format?
  [str]
  (try (UUID/fromString str)
       true
       (catch IllegalArgumentException e false)))