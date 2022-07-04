(ns pyyp.config
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [next.jdbc :as jdbc]
   [pyyp.router :as router]
   [pyyp.worker :as worker]
   [ring.adapter.jetty :refer [run-jetty]]))

(def config (ig/read-string (slurp (io/resource "config/dev.edn"))))

(defmethod ig/init-key :database/connection [_ db-connection]
  (jdbc/get-connection db-connection))

(defmethod ig/init-key :backend/application [_ {:keys [db auth build-research-chan]}]
  (router/application db auth build-research-chan))

(defmethod ig/init-key :backend/server [_ {:keys [application port join?]}]
  (run-jetty application {:port port :join? join?}))

(defmethod ig/halt-key! :backend/server [_ server] (.stop server))

(defmethod ig/init-key :backend/auth [_ config] config)

(defmethod ig/init-key :scrape-dataset/worker [_ {:keys [db source]}]
  (let [task-chan (async/chan)
        config    (assoc source :db-conn db)]
    (worker/task-event-loop task-chan config)
    [task-chan]))

(defmethod ig/halt-key! :scrape-dataset/worker [_ channels]
  (doseq [x channels] (async/close! x)))
