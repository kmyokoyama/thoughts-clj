(ns thoughts.port.config)

(defprotocol Config
  (value-of! [config key]))