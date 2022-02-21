(ns pyyp.config
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [pyyp.router :as router]
            [ring.adapter.jetty :refer [run-jetty]]
            ))

(def config {:database/connection "jdbc:sqlite:resources/db.sqlite"
             :backend/application {:db         (ig/ref :database/connection)
                                   :jwt-secret "ff363172-d6b5-46f4-889e-858dbf200d43" }
             :backend/server      {:application (ig/ref :backend/application)
                                   :port        3000
                                   :join?       false}
             })

(defmethod ig/init-key :database/connection [_ db-connection]
  (jdbc/get-connection db-connection)
  )

(defmethod ig/init-key :backend/application [_ {:keys [db jwt-secret]}]
  (router/application db jwt-secret))

(defmethod ig/init-key :backend/server [_ {:keys [application port join?]}]
  (run-jetty application {:port port :join? join?}))

(defmethod ig/halt-key! :backend/server [_ server]
  (.stop server))
