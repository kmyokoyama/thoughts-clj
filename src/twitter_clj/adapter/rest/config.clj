(ns twitter-clj.adapter.rest.config
  (:require [clojure.string :as string]
            [twitter-clj.application.config :refer [system-config]]))

(def rest-config (get-in system-config [:http :api]))

(defn -path-prefix
  [config path]
  (-> (list (:version config)
            (:path-prefix config)
            path)
      (#(string/join "/" %))
      (string/replace #"/[/]+" "/")
      (#(str "/" %))))

(def path-prefix (partial -path-prefix rest-config))
