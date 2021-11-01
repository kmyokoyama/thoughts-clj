(defproject thoughts-clj "0.0.1-SNAPSHOT"
  :description "Twitter-clone API for experimentation with Clojure."
  :url "https://github.com/kmyokoyama/thoughts-clj.git"
  :license {:name "MIT"
            :url  "https://choosealicense.com/licenses/mit/"}
  :resource-paths ["resources"]
  :profiles {:dev    {:dependencies [[cheshire "5.10.0"]
                                     [clj-http "3.10.0"]
                                     [faker "0.2.2"]
                                     [org.clojure/data.generators "0.1.2"]
                                     [ring/ring-devel "1.8.1"]
                                     [nubank/matcher-combinators "3.3.0"]
                                     [mvxcvi/puget "1.3.1"]]
                      :plugins      [[lein-auto "0.1.3"]
                                     [lein-environ "1.2.0"]
                                     [lein-cljfmt "0.8.0"]
                                     [lein-nsorg "0.3.0"]]
                      :jvm-opts     ["-Dconfig.edn=resources/dev-config.edn"]
                      :env          {:http-host            "127.0.0.1"
                                     :http-port            3000
                                     :http-api-version     "v0"
                                     :http-api-path-prefix "api"
                                     :http-api-jws-secret  "123"
                                     :datomic-uri          "datomic:mem://dev-thoughts"
                                     :redis-uri            "redis://localhost:6379"}}
             :debug  {:jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5000"]}}
  :dependencies [[buddy/buddy-auth "2.2.0"]
                 [buddy/buddy-hashers "1.4.0"]
                 [com.datomic/datomic-free "0.9.5697"]
                 [com.outpace/config "0.13.2"]
                 [environ "1.2.0"]
                 [com.stuartsierra/component "0.4.0"]
                 [com.taoensso/carmine "2.19.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [compojure "1.6.1"]
                 [http-kit "2.3.0"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.reader "1.3.2"]
                 [prismatic/schema "1.1.12"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.0"]
                 [ring-server "0.4.0"]]
  :repl-options {:init-ns dev}
  :main thoughts.application.main
  :aot [thoughts.application.main]
  :aliases {"nsorg-fix"               ["nsorg" "--replace"]
            "cljfmt-fix"              ["cljfmt" "fix"]
            "lint-fix"                ["do" ["nsorg-fix"] ["cljfmt-fix"]]}
  :test-selectors {:unit        :unit
                   :integration :integration})