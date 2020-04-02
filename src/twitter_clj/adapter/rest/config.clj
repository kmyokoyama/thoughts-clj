(ns twitter-clj.adapter.rest.config
  (:require [clojure.string :as string]))

(def rest-config {:version "1" :path-prefix ""}) ;; TODO: This can be read from external configuration.

(defn- version-str
  [config]
  (str "v" (:version config)))

(defn -path-prefix
  [config path]
  (-> (list (version-str config)
            (:path-prefix config)
            path)
      (#(string/join "/" %))
      (string/replace #"/[/]+" "/")
      (#(str "/" %))))

(def path-prefix (partial -path-prefix rest-config))
