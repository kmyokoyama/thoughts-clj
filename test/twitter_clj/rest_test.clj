(ns twitter-clj.rest-test
  (:require [twitter-clj.rest.handler :refer [handler]]
            [clojure.test :refer :all]
            [ring.server.standalone :as s]
            [clj-http.client :as http]))

(def server (atom nil))

(defn start-server [port]
  (println "Starting server...")
  (reset! server
          (s/serve handler {:port port :open-browser? false :auto-reload? false})))

(defn stop-server []
  (println "Stopping server.")
  (.stop @server)
  (reset! server nil))

(use-fixtures :once (fn [f] (start-server 3000) (f) (stop-server)))

(def url "http://localhost:3000/users")

(deftest get-ok
  (testing "Returns ok"
    (is (let [response (http/get url {})]
          (println response)
          true))))