(ns pyyp.worker
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as http]
   [pyyp.db :as db]
   [taoensso.timbre :as timbre]
   [clojure.core.async :as async]))


(defn dataset-exists? [db-conn dataset]
  (seq (db/dataset-by-doi-version db-conn dataset)))


(defn dataset-preconditions [db-conn dataset]
  (timbre/info "Dataset preconditions for " (:doi dataset))
  (assoc dataset :exists? (seq (db/dataset-by-doi-version db-conn dataset))))


(defn- openneuro-api-call [url request-body]
  (http/post url {:content-type :application/json :body request-body}))


(defn metadata-request [url request dataset]
  (let [metadata-request (format request (:doi dataset))
        response         (openneuro-api-call url metadata-request)]
    (assoc dataset :metadata (:body response))))


(defn extract-dataset-from-response [dataset-respond]
  (let [dataset (-> dataset-respond
                    (cheshire/decode true)
                    :data
                    :dataset)]
    {:id         (dataset :id)
     :title      (-> dataset :draft :description :Name)
     :authors    (-> dataset :draft :description :Authors)
     :license    (-> dataset :draft :description :License)
     :url        (-> dataset :metadata :datasetUrl)
     :doi        (-> dataset :draft :description :DatasetDOI)
     :version    (->> dataset :snapshots (sort-by :created) last :tag)
     :created_at (-> dataset :created)
     :is_active  false
     }))


(defn- dataset-preconditions? [dataset]
  (and dataset
       (:metadata dataset)
       (:snapshot dataset)
       (not (get-in dataset [:snapshot :deprecated]))))


(defn insert-dataset-db [db-conn dataset]
  (when (dataset-preconditions? dataset)
    (db/insert-dataset! db-conn
                        (extract-dataset-from-response (:metadata dataset)))))


(defn snapshot-request [url request dataset]
  (let [version-request (format request (:doi dataset) (:version dataset))
        response        (openneuro-api-call url version-request)]
    (assoc dataset :snapshot (:body response))))


(defn launch-images-scraping [files-ch directories-ch dataset]
  (when-let [snapshot-files (-> dataset
                                :snapshot
                                (cheshire/decode true)
                                (get-in [:data :snapshot :files])
                                )]
    (let [files       (filter #(nil? (:directory %)) snapshot-files)
          directories (filter :directory snapshot-files)]
      (async/into files files-ch)
      (async/into directories directories-ch)
      ))
  )
