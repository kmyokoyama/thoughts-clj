(ns integration.thoughts.adapter.http.helper
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [thoughts.adapter.http.util :refer [path-prefix]]
            [thoughts.port.config :as p.config])
  (:import (com.stuartsierra.component Lifecycle)
           (java.time ZonedDateTime)))

;; HttpClient

(defn ->url
  [resource config]
  (let [api-version (p.config/value-of! config :http-api-version)
        api-path-prefix (p.config/value-of! config :http-api-path-prefix)
        host (p.config/value-of! config :http-host)
        port (p.config/value-of! config :http-port)
        url (str "http://" host ":" port)]
    (str url (path-prefix api-version api-path-prefix resource))))

(defn get-body [{:keys [body]}]
  (if (string? body)
    (json/read-str body :key-fn keyword)
    body))

(defn ->result
  [response]
  (let [body (get-body response)]
    {:response response :body body :result (:result body)}))

(def ^:private unexceptional-status #(or (<= 200 % 299) (<= 400 % 401)))

(defprotocol HttpClient
  (get!
    [http-client url token]
    [http-client url token params])

  (post!
    [http-client url token]
    [http-client url token params]))

(defrecord SimpleHttpClient [config]
  Lifecycle
  (start
    [this]
    this)

  (stop
    [this]
    this)

  HttpClient
  (get! [_this resource token]
    (-> resource
        (->url config)
        (client/get {:oauth-token          token
                     :unexceptional-status unexceptional-status})
        ->result))

  (get! [_this resource token params]
    (-> resource
        (->url config)
        (client/get {:query-params         params
                     :oauth-token          token
                     :unexceptional-status unexceptional-status})
        ->result))

  (post! [_this resource body]
    (-> resource
        (->url config)
        (client/post {:content-type         :json
                      :form-params          body
                      :unexceptional-status unexceptional-status})
        ->result))

  (post! [_this resource token body]
    (-> resource
        (->url config)
        (client/post {:content-type         :json
                      :form-params          body
                      :oauth-token          token
                      :unexceptional-status unexceptional-status})
        ->result)))

(defn make-http-client
  []
  (map->SimpleHttpClient {}))

;; Datetime manipulation.

(defn str->EpochSecond
  [date]
  (-> date
      (ZonedDateTime/parse)
      (. toInstant)
      (. getEpochSecond)))