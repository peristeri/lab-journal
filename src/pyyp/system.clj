(ns pyyp.system
  (:gen-class)
  (:require [integrant.core :as ig]
            [reitit.ring :as ring]
            [pyyp.config :as config]
            ))

(defn routes [env]
  [
   ["/signin" {:post nil}]
   ])

(defn application [db-handler]
  (ring/ring-handler)
  )

(defn -main
  [& args]
  (ig/init config/config)
  )
