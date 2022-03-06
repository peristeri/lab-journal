(ns pyyp.config
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [pyyp.router :as router]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.core.async :as async]
            ))

(def config {:database/connection "jdbc:sqlite:resources/db.sqlite"
             :backend/application
             {:db                  (ig/ref :database/connection)
              :jwt-secret          "ff363172-d6b5-46f4-889e-858dbf200d43"
              :build-research-chan (ig/ref :build-research/channel)}
             :backend/server
             {:application (ig/ref :backend/application)
              :port        3000
              :join?       false}
             :build-research/channel
             {:size 64}
             :build-research/worker
             {:channel (ig/ref :build-research/channel)
              :db      (ig/ref :database/connection)}})

(defmethod ig/init-key :database/connection [_ db-connection]
  (jdbc/get-connection db-connection))

(defmethod ig/init-key :backend/application [_ {:keys [db jwt-secret worker-channel]}]
  (router/application db jwt-secret worker-channel))

(defmethod ig/init-key :backend/server [_ {:keys [application port join?]}]
  (run-jetty application {:port port :join? join?}))

(defmethod ig/halt-key! :backend/server [_ server]
  (.stop server))

(defmethod ig/init-key :build-research/channel [_ {:keys [size]}]
  (async/chan (async/buffer size)))

(defmethod ig/halt-key! :build-research/channel [_ channel]
  (async/close! channel))

(defmethod ig/init-key :build-research/worker [_ {:keys [channel]}]
  (async/go-loop []
    (when-let [message (async/<! channel)]
      (println "Message received: " message)
      (recur))))
