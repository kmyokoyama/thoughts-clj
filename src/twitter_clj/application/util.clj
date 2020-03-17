(ns twitter-clj.application.util)

(defrecord Operation [status result])

(defn success [result] (->Operation :ok result))
(defn error [error-ctx] (->Operation :error error-ctx))

(defn process
  ([op f]
   (process op f identity))

  ([op f e]
   (case (:status op)
     :ok (success (f (:result op)))
     :error (error (e (:result op))))))