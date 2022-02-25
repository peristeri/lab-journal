(ns pyyp.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [buddy.hashers :refer [encrypt]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.util UUID)))


(defn create-tables [db]
  (try
    (let [tables (-> (io/resource "db/sqlite/init-db.sql")
                     slurp
                     (str/split #"\n\n")
                     )]
      (jdbc/with-transaction [tx db]
        (for [stmt tables]
          (jdbc/execute! tx [stmt]))))

    (catch Exception e (println "Exception: " (ex-message e) (ex-cause e)))))


(def roles ["researcher" "worker"])


(defn create-account [db-conn {:keys [username password role]}]
  (let [hashed (encrypt  password)
        values [(UUID/randomUUID) username hashed role]
        data   (zipmap [:id :username :password :role] values)]
    (sql/insert! db-conn :account data)))


(defn account-by-username [db-conn username]
  (first (sql/find-by-keys db-conn :account {:username username :is_active true})))


(defn verify-account [db-conn username id]
  (first (sql/find-by-keys db-conn :account {:username username :id id :is_active true})))


(defn create-reseach-by-leader-id [db-conn {:keys [specification] :as params}]
  (let [data (-> params
                 (assoc :id            (UUID/randomUUID)
                        :specification (pr-str specification)))]
    (when data
      (sql/insert! db-conn :research data))))
