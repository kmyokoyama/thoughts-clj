(ns twitter-clj.main
  (:require [org.httpkit.server :as server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer :all]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [twitter-clj.operations :as app])
  (:gen-class))

(defn get-parameter
  [req param]
  (param (:params req)))

(def people-collection (atom []))

(defn add-person
  [firstname surname]
  (swap! people-collection conj {:firstname (str/capitalize firstname)
                                 :surname (str/capitalize surname)}))

(add-person "Kazuki" "Yokoyama")

(defn simple-body-page
  [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Hello world"})

(defn request-example
  [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->>
           (pp/pprint req)
           (str "Request object: " req))})

(defn hello-name
  [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->
           (pp/pprint req)
           (str "Hello " (get-parameter req :name)))})

(defn people-handler
  [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str (json/write-str @people-collection))})

(defn add-person-handler
  [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (->
           (let [p (partial get-parameter req)]
             (str (json/write-str (add-person (p :firstname) (p :surname))))))})

(defn add-user
  [req]
  (let [name (get-parameter req :name)
        email (get-parameter req :email)
        nickname (get-parameter req :nickname)]
    (app/create-user name email nickname)
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "success"}))

(defn get-users
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (app/get-users)})

(defn add-tweet
  [req]
  (let [user-id (get-parameter req :user-id)
        text (get-parameter req :text)
        tweet-id (app/add-tweet user-id text)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str tweet-id)}))

(defn get-tweets-by-user
  [req]
  (let [user-id (get-parameter req :user-id)
        tweets (app/get-tweets-by-user user-id)]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str (vec (map #(into {} %) tweets)) :value-fn app/value-writer)}))


(defroutes app-routes
           (GET "/" [] simple-body-page)
           (GET "/user" [] add-user)
           (GET "/users" [] get-users)
           (GET "/tweet" [] add-tweet)
           (GET "/tweets" [] get-tweets-by-user)
           (GET "/hello" [] hello-name)
           (GET "/people" [] people-handler)
           (GET "/add-people" [] add-person-handler)
           (route/not-found "Error, page not found!"))

(defn -main
  [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (server/run-server (wrap-defaults #'app-routes site-defaults) {:port port})
    ;(server/run-server #'app-routes {:port port})
    (println (str "Running server at http://127.0.01:" port "/"))))

