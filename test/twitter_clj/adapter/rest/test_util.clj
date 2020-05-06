(ns twitter-clj.adapter.rest.test-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [twitter-clj.adapter.rest.util :refer [path-prefix]]))

;; REST API.

;; Wrappers for requests.

(def unexceptional-status #(or (<= 200 % 299) (<= 400 % 401)))

(defn get
  ([url token]
   (client/get url {:oauth-token token
                    :unexceptional-status unexceptional-status}))

  ([url token params]
   (client/get url {:query-params params
                    :oauth-token token
                    :unexceptional-status unexceptional-status})))

(defn post
  ([url body]
   (client/post url {:content-type :json
                     :form-params body
                     :unexceptional-status unexceptional-status}))

  ([url token body]
   (client/post url {:content-type :json
                     :form-params body
                     :oauth-token token
                     :unexceptional-status unexceptional-status})))

;; API responses manipulation.

(defn get-body [{:keys [body]}]
  (if (string? body)
    (json/read-str body :key-fn keyword)
    body))

(defn parse-response
  [response]
  (let [body (get-body response)]
    {:response response :body body :result (:result body)}))

(def post-and-parse (comp parse-response post))

(def get-and-parse (comp parse-response get))

;; API paths manipulation.

(defn resource-path
  [url path]
  (str url (path-prefix path)))