(ns twitter-clj.test-utils
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]))

(defn resource-path
  [url path]
  (str url "/" path))

(defn body-as-json [{:keys [body]}]
  (if (string? body)
    (json/read-str body :key-fn keyword)
    body))

(defn new-user
  [name email nickname]
  {:name name :email email :nickname nickname})

(defn new-tweet
  [user-id text]
  {:user-id user-id :text text})