(ns twitter-clj.adapter.rest.test_utils
  (:require [clojure.data.json :as json]
            [clojure.data.generators :as random]
            [clj-http.client :as client]
            [twitter-clj.adapter.rest.test_configuration :refer [url]]))

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

(def resource (partial resource-path url))

(defn random-uuid
  []
  (random/uuid))

(defn new-user
  []
  {:name (random/string) :email (random/string) :username (random/string)})

(defn new-tweet
  ([]
   (new-tweet (random-uuid)))

  ([user-id]
   {:user-id user-id :text (random/string)})

  ([user-id text]
   {:user-id user-id :text text}))