(ns twitter-clj.adapter.rest.util
  (:require [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]))

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

(defn get-from-body
  [req param]
  (param (:body req)))

(defn get-parameter
  [req param]
  (param (:params req)))

(def ^:const status-success
  {:status "success"})

(def ^:const status-failure
  {:status "failure"})

(defn add-success-result
  [result]
  (assoc status-success :result result))

(defn add-failure-result
  [result]
  (assoc status-failure :result result))

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

(def ok-with-success (comp ok-response to-json add-success-result))
(def ok-with-failure (comp ok-response to-json add-failure-result))
(def created (comp created-response to-json add-success-result))


(defn new-token
  [secret user-id role]
  (jwt/sign {:user-id user-id :role role} secret))

(defn add-leading-slash
  [path]
  (if (clojure.string/starts-with? path "/")
    path
    (str "/" path)))

(defn remove-duplicate-slashes
  [path]
  (clojure.string/replace path #"/[/]+" "/"))

(defn join-path
  [& path-parts]
  (-> (clojure.string/join "/" path-parts)
      (clojure.string/replace #"://" "!")
      (remove-duplicate-slashes)
      (clojure.string/replace #"!" "://")))

(defn f-id
  [id]
  (str "[ID: " id "]"))

(defn f
  ([entity]
   (if-let [id (:id entity)]
     (f-id id)
     (str "[" entity "]")))

  ([entity attr]
   (if-let [attr-val (attr entity)]
     (let [formatted-attr (-> attr (name) (clojure.string/replace #"-" ""))]
       (str "[" formatted-attr ": " attr-val "]"))
     entity)))