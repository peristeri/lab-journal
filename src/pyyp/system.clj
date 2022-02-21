(ns pyyp.system
  (:gen-class)
  (:require [integrant.core :as ig]
            [pyyp.config :as config]
            ))

(defn -main [& _]
  (ig/init config/config)
  )
