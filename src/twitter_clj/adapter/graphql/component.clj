(ns twitter-clj.adapter.graphql.component
  (:require [twitter-clj.adapter.graphql.resolver :as resolver]
            [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as lacinia.schema]
            [clojure.edn :as edn]
            [taoensso.timbre :as log]))

(defrecord GraphQLServer [schema service]
  component/Lifecycle
  (start
    [this]
    (log/info "Starting GraphQL server")
    (assoc this :schema (-> "/home/kmyokoyama/repositories/twitter-clj/src/twitter_clj/adapter/graphql/schema.edn"
                            slurp
                            edn/read-string
                            (attach-resolvers {:get-hero  resolver/get-hero
                                               :get-droid (constantly {})})
                            lacinia.schema/compile)))

  (stop
    [this]
    (log/info "Stopping GraphQL server")
    this))

(defn make-graphql
  []
  (map->GraphQLServer {}))