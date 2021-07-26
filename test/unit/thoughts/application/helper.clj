(ns unit.thoughts.application.helper
  (:require [clojure.data.generators :as generators]
            [clojure.string :as string]
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
   (->> (generators/string) (take n) (apply str))))

(defn random-text
  []
  (string/join "\n" (take 2 (lorem/paragraphs))))

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

(defn uuid?
  [str]
  (try (UUID/fromString str)
       true
       (catch IllegalArgumentException e false)))