(ns fixtures
  (:require [pyyp.db :as db]
            [next.jdbc.sql :as sql]
            [faker.internet :as fa]
            [faker.lorem :as fl]
            [clojure.string :as str]))

;; Use faker to create a sample instance of the system data

;; 3 workers and 2 researchers accounts
;; 1 new research and 1 completed research
;; 5 datasets with >100 images

;; TODO add random dates to create_at
;; TODO add random to is_active

(defn fake-entry []
  {:is_active (rand-nth [true false])})

(defn fake-account
  ([] (fake-account (rand-nth db/roles)))
  ([role] {:username (fa/user-name)
           :password (apply str (take 3 (fl/words)))
           :role     role}))


(defn seed-accounts [db-conn num]
  (dotimes [_ num] (db/create-account! db-conn (fake-account))))

(defn fake-research
  [account-id]
  {:leader                 account-id
   :title                  (first (fl/sentences))
   :specification          "{}"
   :application_repository (fa/domain-name)})

(defn seed-researches [db-conn num]
  (dotimes [_ num]
    (->> (sql/query db-conn ["select * from account order by random() limit 1"])
         first
         :account/id
         fixtures/fake-research
         (db/create-research! db-conn))))

(defn fake-datasets []
  (let [url (str (fa/domain-name) "/" (str/join "/" (take 3 (fl/words))))]
    {:name    (fa/user-name)
     :license (first (fl/sentences))
     :url     url
     :version (first (fl/words))}))


(defn fake-dimensions []
  (str/join "x" (repeatedly 3 #(* 100 (inc (rand-int 35))))))


(defn fake-image [dataset-id]
  (let [url         (str (fa/domain-name) "/" (str/join "/" (take 4 (fl/words))))
        anatomy     (rand-nth ["knee" "shoulder" "torso" "head" nil])
        is_labelled (rand-nth [true false])
        orientation (rand-nth ["RPI" "LPI" "RSP" nil])
        size        (fake-dimensions)
        dimension   (fake-dimensions)]
    {:url         url
     :anatomy     anatomy
     :is_labelled is_labelled
     :orientation orientation
     :size        size
     :dimension   dimension
     :details     "{}"
     :dataset_id  dataset-id}))


(defn seed-images [db-conn dataset-id num-images]
  (let [images (for [_ (range num-images)] (fake-image dataset-id))]
    (db/insert-images! db-conn images)))


(defn seed-datasets [db-conn num-set num-images]
  (dotimes [_ num-set]
    (let [data    (fake-datasets)
          dataset (db/insert-dataset! db-conn data)]
      (seed-images db-conn (:id dataset) num-images))))


(defn fake-experiment [research dataset]
  {:research_id         (:research/id research)
   :dataset_id          (:dataset/id dataset)
   :application_version (first (fl/words))})


(defn seed-experiments [db-conn num]
  (let [researches (sql/query db-conn ["select * from research order by random() limit ?" num])]
    (for [research researches
          dataset  (sql/query db-conn ["select * from dataset order by random() limit ?" (inc (rand-int 4))])]
      (fake-experiment research dataset))))

(defn seed-db [db]
  (fixtures/seed-accounts db 5)
  (fixtures/seed-researches db 50)
  (fixtures/seed-datasets db 10 300)
  (fixtures/seed-experiments db 10))
