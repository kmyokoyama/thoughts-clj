(ns twitter-clj.test-utils
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clojure.data.generators :as random]))

(defn resource-path
  [url path]
  (str url "/" path))

(defn body-as-json [{:keys [body]}]
  (if (string? body)
    (json/read-str body :key-fn keyword)
    body))

(defn new-user
  []
  {:name (random/string) :email (random/string) :nickname (random/string)})

(defn new-tweet
  ([user-id]
   {:user-id user-id :text (random/string)})

  ([user-id text]
   {:user-id user-id :text text}))