(ns pyyp.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [buddy.hashers :refer [encrypt]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.util UUID)))


(def roles ["researcher" "worker"])


(defn create-tables [db]
  (try
    (let [tables (-> (io/resource "db/sqlite/init-db.sql")
                     slurp
                     (str/split #"\n\n"))]
      (jdbc/with-transaction [tx db]
        (for [stmt tables]
          (jdbc/execute! tx [stmt]))))
    (catch Exception e (println "Exception: " (ex-message e) (ex-cause e)))))


(defn- prepare-new-entry [values]
  (-> values
      (assoc :id (UUID/randomUUID))))


(defn- prepare-new-account [values]
  (-> values
      (prepare-new-entry)
      (assoc :password (encrypt (:password values)))))

(defn- prepare-new-research [values]
  (-> values
      (prepare-new-entry)
      (assoc :specification (pr-str (:specification values)))))


(defn create-account! [db-conn values]
  (let [data (prepare-new-account values)]
    (sql/insert! db-conn :account data)
    data))


(defn create-research! [db-conn values]
  (let [data (prepare-new-research values)]
    (when data
      (sql/insert! db-conn :research data)
      data)))


(defn account-by-username [db-conn username]
  (first (sql/find-by-keys db-conn :account {:username username :is_active true})))


(defn verify-account [db-conn username id]
  (first (sql/find-by-keys db-conn :account {:username username :id id :is_active true})))


(defn create-dataset! [db-conn values]
  (let [data (prepare-new-entry values)]
    (sql/insert! db-conn :dataset data)
    data))


(defn create-images! [db-conn values]
  (doseq [value values]
    (sql/insert! db-conn :image (prepare-new-entry value))))
