(ns twitter-clj.adapter.rest.test_configuration
  (:require [clojure.test :refer :all]))

(def ^:const port 3000)
(def ^:const url (str "http://localhost:" port "/"))

(def system-config {:server-config {:port port}})