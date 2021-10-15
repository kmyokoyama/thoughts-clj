(ns thoughts.adapter.http.util
  (:require [buddy.sign.jwt :as jwt]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [thoughts.application.config :as config]))

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

(defn get-user-id
  [req]
  (get-in req [:identity :user-id]))

(defn get-session-id
  [req]
  (get-in req [:identity :session-id]))

(def ^:const status-success
  {:status "success"})

(def ^:const status-failure
  {:status "failure"})

(defn add-success-result
  [result]
  (cond-> status-success
    (sequential? result) (assoc :total (count result))
    true (assoc :result result)))

(defn add-failure-result
  [result]
  (assoc status-failure :result result))

(defn to-json
  [r]
  (json/write-str r :value-fn value-writer))

(defn json-response
  [status body]
  {:status  status
   :headers {"Content-Type" "application/json"}
   :body    body})

(def ok-response (partial json-response 200))
(def created-response (partial json-response 201))
(def bad-request-response (partial json-response 400))
(def unauthorized-response (partial json-response 401))

(def ok-with-success (comp ok-response to-json add-success-result))
(def created (comp created-response to-json add-success-result))
(def bad-request (comp bad-request-response to-json add-failure-result))
(defn unauthorized
  []
  (-> {:cause "you are not logged in"}
      (add-failure-result)
      (to-json)
      (unauthorized-response)))
(defn internal-server-error
  []
  {:status 501 :headers {"Content-Type" "text/plain"}})

(defn new-token
  [secret user-id role session-id]
  (jwt/sign {:user-id user-id :role role :session-id session-id} secret {:alg :hs512}))

(def create-token (partial new-token config/http-api-jws-secret))

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

(defn path-prefix
  [path]
  (->> (list config/http-api-path-prefix
             config/http-api-version
             path)
       (apply join-path)
       (add-leading-slash)))

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

(defn log-failure
  [& args]
  (log/warn (clojure.string/join " " (cons "Failure -" args))))

(defn str-exception
  "The first line is the exception's message and the next lines are the stacktrace with leading two spaces."
  [e]
  (str (.getMessage e) "\n" (apply str (cons "  " (interpose "\n  " (.getStackTrace e))))))

(defn schema-error-context
  [schema-error]
  (let [supplied (:value schema-error)
        error-fields (:error schema-error)]
    (reduce (fn [acc k]
              (if (k supplied)
                (assoc acc k (str (k supplied) " (wrong type)"))
                (assoc acc k "(missing)")))
            {}
            (keys error-fields))))

(defn str->int
  [s]
  (try
    (Integer/parseInt s)
    (catch NumberFormatException _ nil)))