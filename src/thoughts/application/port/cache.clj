(ns thoughts.application.port.cache)

(defprotocol Cache
  (update-session! [cache session])
  (update-feed! [cache user-id feed ttl])
  (fetch-feed! [cache user-id limit offset])
  (remove-session! [cache criteria]))
