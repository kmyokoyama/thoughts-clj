(ns twitter-clj.infra.in-mem)

(def users (atom {})) ;; It could also be a ref.
(def tweets (atom {})) ;; It could also be a ref.

;; Driven-side.

(defn save-user-in-memory
  [{user-id :id :as user}]
  (swap! users (fn [users] (assoc users user-id user))))

(defn save-tweet-in-memory
  [{tweet-id :id :as tweet}]
  (swap! tweets (fn [tweets] (assoc tweets tweet-id tweet))))

(defn inspect-users-in-memory
  []
  @users)

(defn inspect-tweets-in-memory
  []
  @tweets)

(defn get-awards-by-id-in-memory
  [id]
  (get @tweets id))

;; System configuration.

(def system-map
  {:update-user-fn!        save-user-in-memory
   :update-tweet-fn!       save-tweet-in-memory
   :inspect-users-fn! inspect-users-in-memory
   :inspect-tweets-fn! inspect-tweets-in-memory})




