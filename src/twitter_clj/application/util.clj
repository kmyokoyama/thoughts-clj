(ns twitter-clj.application.util)

;; Logging.

(defn highlight
  [& args]
  (println "=============================================")
  (apply println args)
  (println "============================================="))
