(ns twitter-clj.adapter.rest.util
  (:require [clojure.data.json :as json]))

;; Private functions.

(defn- is-better-str
  [key]
  (or
    (= key :id)
    (some #(.endsWith (str key) %) ["-id", "-date"])))

(defn- value-writer
  [key value]
  (if (is-better-str key)
    (str value)
    value))

;; Public functions.

(defn get-parameter
  [req param]
  (param (:params req)))

(def ^:const status-success
  {:status "success"})

(def ^:const status-failure
  {:status "failure"})

(defn add-response-info
  [info]
  (assoc status-success :result info))

(defn to-json
  [r]
  (json/write-str r :value-fn value-writer))

(defn ok-response
  "Respond with 200 (HTTP OK)"
  [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body body})

(defn created-response
  "Respond with 201 (HTTP Created)"
  [body]
  {:status 201
   :headers {"Content-Type" "application/json"}
   :body body})

(def respond-with-ok (comp ok-response to-json add-response-info))
(def respond-with-created (comp created-response to-json add-response-info))