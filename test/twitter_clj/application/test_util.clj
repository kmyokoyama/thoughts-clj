(ns twitter-clj.application.test-util
  (:require [clojure.test :refer :all]
            [faker.name :as name]
            [faker.internet :as internet]
            [faker.lorem :as lorem]
            [clojure.string :refer [join]])
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

(defn now [] (ZonedDateTime/now))

;; Checkers.

(defn uuid-format?
  [str]
  (try (UUID/fromString str)
       true
       (catch IllegalArgumentException e false)))