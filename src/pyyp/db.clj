(ns pyyp.db
  (:require [next.jdbc :as jdbc]
            [honeysql.core :as honeysql]
            [next.jdbc.sql :as sql]
            [buddy.hashers :refer [encrypt]]
            [clojure.java.io :as io]
            [clojure.string :as str]))


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
        data   [[username hashed (java.util.UUID/randomUUID) role]]]
    (sql/query db-conn
               (honeysql/format
                 {:insert-into :account
                  :columns     [:username :password :id :role]
                  :values      data}))))

(defn account-by-username [db-conn username]
  (first (sql/find-by-keys db-conn :account {:username username})))
