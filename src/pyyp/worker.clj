(ns pyyp.worker
  (:require [clojure.core.async :as async]
            [taoensso.timbre :as timbre]
            [clj-http.client :as http]
            [cheshire.core :as cheshire]
            [pyyp.db :as db]
            [clojure.java.io :as io]))


(defn dataset-exists? [dataset-chan db-conn]
  (let [out-chan (async/chan)]
    (async/go-loop [dataset (async/<! dataset-chan)]
      (if (nil? dataset)
        (do
          (timbre/info "Channels closed")
          (async/close! out-chan))
        (do
          (let [exists? (seq (db/dataset-by-doi-version db-conn dataset))
                result  (assoc dataset :exists? exists?)]
            (async/>! out-chan result))
          (recur (async/<! dataset-chan)))))
    out-chan))

(defn- openneuro-api-call [url request-body]
  (http/post url {:content-type :application/json :body request-body}))

(defn- metadata-preconditions? [dataset]
  (nil? (:exists dataset)))

(defn metadata-action [url request dataset]
  (let [metadata-request (format request (:doi dataset))
        response         (openneuro-api-call url metadata-request)]
    (spit (io/resource (str (:doi dataset) "-" (:version dataset) "-metadata")) response)
    (assoc dataset :metadata (:body response))))

(defn request-dataset-metadata
  "For a dataset to be available, it requires that the api requests returns
  information about the dataset"
  [dataset-chan dataset-url dataset-request]
  (let [out-chan (async/chan)]
    (async/go-loop [dataset (async/<! dataset-chan)]
      (if (nil? dataset)
        (do
          (timbre/log "Closing channels")
          (async/close! out-chan))
        (do
          (if (metadata-preconditions? dataset)
            (let [response (metadata-action
                             dataset-url
                             dataset-request
                             dataset)]
              (async/>! out-chan response))
            (println "Dataset" (:doi dataset) "already exists"))
          (recur (async/<! dataset-chan)))))
    out-chan))


(defn extract-dataset-from-response [dataset-respond]
  (let [dataset (-> dataset-respond
                    (cheshire/decode true)
                    :data
                    :dataset)]
    {:title      (-> dataset :draft :description :Name)
     :authors    (-> dataset :draft :description :Authors)
     :license    (-> dataset :draft :description :License)
     :url        (-> dataset :metadata :datasetUrl)
     :doi        (-> dataset :draft :description :DatasetDOI)
     :version    (->> dataset :snapshots (sort-by :created) last :tag)
     :created_at (-> dataset :created)
     :is_active  false
     }))

(defn save-dataset-metadata
  "Save the contents of :details into the dataset table"
  [dataset-chan db-conn]
  (async/go-loop [dataset (async/<! dataset-chan)]
    (when-let [metadata (:metadata dataset)]
      (db/insert-dataset! db-conn (extract-dataset-from-response metadata))
      (recur (async/<! dataset-chan)))))

(defn- version-preconditions?
  [response]
  (and response (not (:deprecated response)))
  )

(defn- version-action [url request dataset]
  (let [version-request (format request (:doi dataset) (:version dataset))
        response        (openneuro-api-call url version-request)]
    (spit (io/resource (str (:doi dataset) "-" (:version dataset) "-version")) response)
    (assoc dataset :snapshot (:body response))))

(defn request-dataset-snapshot
  "The version should match to the one provided."
  [dataset-chan dataset-url dataset-request]
  (let [out-chan (async/chan) version-chan (async/chan)]
    (async/go-loop [dataset (async/<! dataset-chan)]
      (if (nil? dataset)
        (do (async/close! out-chan) (async/close! version-chan))
        (do
          (let [response (version-action dataset-url dataset-request dataset)]
            (if (version-preconditions? response)
              (async/>! out-chan response)
              (timbre/warn
                (str "Dataset " (:doi dataset) " with version" (:version dataset) " not valid for downloading."))))
          (recur (async/<! dataset-chan)))))
    out-chan))
