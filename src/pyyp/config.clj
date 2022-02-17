(ns pyyp.config
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]))

(def config {:database/connection "jdbc:sqlite:resources/db.sqlite"})

(defmethod ig/init-key :database/connection [_ db-connection]
  (let [conn (jdbc/get-connection db-connection)]
    conn))
