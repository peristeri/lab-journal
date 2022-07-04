(ns pyyp.worker
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as http]
   [clojure.core.async :as async]
   [integrant.repl.state :as ig-state]
   [pyyp.db :as db]
   [taoensso.timbre :as timbre])
  (:import
   (java.util UUID)))

(def Task-Definition
  {:task   :scrape-dataset
   :source :openneuro
   :steps  [:validate-arguments
            :scrape-metadata
            :scrape-snapshot
            :insert-dataset-db
            :scrape-images]})


(defn validate-arguments [db-conn dataset]
  (timbre/info "Validate dataset arguments for " (:doi dataset) dataset)
  (let [exists? (seq (db/dataset-by-doi-version db-conn dataset))]
    (assoc dataset :valid? (not exists?))))


(defn- openneuro-api-call [url request-body]
  (http/post url {:content-type :application/json :body request-body}))


(defn metadata-request [url request dataset]
  (timbre/info "Request metadata for dataset " (:doi dataset))
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
  (timbre/info "Insert dataset into the DB " (:doi dataset))
  (when (dataset-preconditions? dataset)
    (db/insert-dataset! db-conn
                        (extract-dataset-from-response (:metadata dataset)))))


(defn snapshot-request [url request dataset]
  (timbre/info "Request snapshot for dataset " (:doi dataset))
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


(defn scrape-dataset-handler [request]
  (let [params  (:body request)
        env     (:env request)
        task-ch (get-in request [:env :channel])
        message {:transaction-id (UUID/randomUUID)
                 ;;   :start-time     (Date/now)
                 :params         params
                 :env            env
                 :steps          [:validate-arguments
                                  :scrape-metadata
                                  :scrape-snapshot
                                  :insert-dataset-db
                                  :scrape-images]
                 }]
    (async/>! task-ch message)))


(defmulti task-step (juxt #(get-in % [:task :params :source]) #(-> % :task :steps first)))

(defmethod task-step [:openneuro :validate-arguments] [request]
  (timbre/info :validate-arguments)
  (let [db-conn (get-in request [:config :db-conn])
        params  (get-in request [:task :params])
        results (validate-arguments db-conn params)]
    (assoc-in request [:task :results :validate-arguments] results)))

(defmethod task-step [:openneuro :scrape-metadata] [request]
  (timbre/info :scrape-metadata)
  (let [source  (get-in request [:config :source :openneuro])
        params  (get-in request [:task :params])
        results (metadata-request (:url source)
                                  (:metadata-request source)
                                  params)]
    (assoc-in request [:task :results :scrape-metadata] results)))

(defmethod task-step [:openneuro :scrape-snapshot] [request]
  (timbre/info :scrape-snapshot)
  (let [source  (get-in request [:config :source :openneuro])
        params  (get-in request [:task :params])
        results (snapshot-request (:url source)
                                  (:snapshot-request source)
                                  params)]
    (assoc-in request [:task :results :scrape-snapshot] results)))

(defmethod task-step [:openneuro :insert-dataset-into-db] [request]
  (timbre/info :insert-dataset-into-db)
  (let [db-conn  (get-in request [:config :db-conn])
        params   (get-in request [:task :params])
        response (insert-dataset-db db-conn params)]
    (assoc-in request [:task :results :insert-dataset-into-db] response)))

(defmethod task-step [:openneuro :scrape-images] [request]
  (println :scrape-images)
  request)

(defn task-event-loop [task-chan config]
  (async/go-loop[task (async/<! task-chan)]
    (let [response        (task-step {:task task :config config})
          remaining-steps (next (:steps response))]
      (if (and (not (:errors response))
               remaining-steps)
        (async/>! task-chan (assoc response :steps remaining-steps))
        (println "Task completed")
        ))
    (recur (async/<! task-chan))))


(comment
  (def test-event {
                   :params {:source  :openneuro
                            :doi     "ds004056"
                            :version "1.0.2"}
                   :steps  [:validate-arguments
                            :scrape-metadata
                            :scrape-snapshot
                            :insert-dataset-into-db
                            :scrape-images
                            ]
                   })

  (async/put! test-chan test-event)
  (def tasks {:task test-event :config {:db-conn (ig-state/system :database/connection)}})
  (task-step tasks)
  (async/take! test-chan println)
  (async/close! test-chan)
  )
;; => nil
