(ns thoughts.application.test-util
  (:require [clj-http.client :as client]
            [clojure.data.generators :as random]
            [clojure.data.json :as json]
            [clojure.string :refer [join]]
            [clojure.test :refer :all]
            [faker.internet :as internet]
            [faker.lorem :as lorem]
            [faker.name :as name])
  (:import [java.time ZonedDateTime]
           [java.util UUID]))

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

(defn random-thought
  ([]
   (random-thought (random-uuid)))

  ([text]
   {:text text}))

;; Checkers.

(defn uuid-format?
  [str]
  (try (UUID/fromString str)
       true
       (catch IllegalArgumentException e false)))