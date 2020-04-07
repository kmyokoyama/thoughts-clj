(defproject twitter-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :resource-paths ["resources"]
  :profiles {:dev {:dependencies [[org.clojure/data.generators "0.1.2"]
                                  [cheshire "5.10.0"]
                                  [clj-http "3.10.0"]
                                  [midje "1.9.9"]
                                  [faker "0.2.2"]]
                   :plugins [[lein-midje "3.2.1"]]
                   :jvm-opts ["-Dconfig.edn=resources/dev-config.edn"]}}
  :dependencies [[buddy/buddy-auth "2.2.0"]
                 [http-kit "2.3.0"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/data.json "0.2.6"]
                 [com.outpace/config "0.13.2"]
                 [com.stuartsierra/component "0.4.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.0"]
                 [ring-server "0.4.0"]]
  :repl-options {:init-ns twitter-clj.core}
  :main twitter-clj.application.main)