(ns user
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [fixtures]
   [integrant.repl :as ig-repl]
   [integrant.repl.state :as ig-state]
   [next.jdbc.sql :as sql]
   [pyyp.config :as config]
   [pyyp.db :as db]
   [pyyp.middleware :as middleware]
   [pyyp.worker :as worker]))

(ig-repl/set-prep! (fn [] config/config))


(defn login-token [username password]
  (-> (client/post "http://localhost:3000/login"
                   {:basic-auth   [username password]
                    :content-type :json
                    :accept       :json})
      :body
      (cheshire/parse-string)
      (get "account/token")))


(defn create-research [username title token]
  (let [body (cheshire/encode {:leader          username
                               :title           title
                               :specification   {:resolution "20dpi"}
                               :data_repository "git://neuro-lab/"
                               :version         "2.1.1"
                               })]
    (client/put "http://localhost:3000/api/research"
                {:oauth-token   token
                 :body          body
                 :content-type  :json
                 :accept        :json
                 :save-request? true
                 :debug-body    true})))


(defn create-dataset
  "The common practice of creating a dataset is to scrape a existing dataset from open datasets
  Let's take https://openneuro.org/datasets/ds004056/versions/1.0.2 as a example"
  [username password]
  (let [token   (login-token username password)
        doi     "ds004056"
        version "1.0.2"
        dataset (cheshire/encode {:doi doi :version version :username username})]
    dataset
    #_(client/post "http://localhost:3000/api/dataset" {:oauth-token token
                                                        :body        dataset})
    ))

(comment
  (def db (ig-state/system :database/connection))
  (def auth (ig-state/system :backend/auth))
  (def rand-account
    (first (sql/query db ["select * from account order by random() limit 1"])))
  (def token (middleware/create-jwt-token auth rand-account))
  (create-dataset "researcher" "abc123")
  )

(comment
  (ig-repl/reset-all)
  (let [router (ig-state/system :backend/application)]
    (router {:request-method :post
             :uri            "/login"
             :basic-auth     ["researcher" "abc123"]}))

  )


(comment
  (def router (ig-state/system :backend/application))
  (router {:request-method :post
           :uri            "/login"
           :basic-auth     ["researcher" "abc123"]})
  (router {:request-method :post
           :uri            "/api/dataset"
           :oauth-token    token
           :body-params    (cheshire/generate-string {:source "openneuro" :doi "ds004056" :version "1.0.2"})})
  (router {:request-method :get
           :uri            "/api/dataset"
           :oauth-token    token})
  (login-token "researcher" "abc123")
  (client/get "http://localhost:3000/api/dataset" {:oauth-token token})
  (client/get "http://localhost:3000/api/research" {:oauth-token token})
  (client/post "http://localhost:3000/login"
               {:basic-auth   ["researcher" "abc123"]
                :content-type :json
                :accept       :json}))

(comment
  (def db (ig-state/system :database/connection))
  (db/create-tables db)
  (db/create-account! db {:username "researcher" :password "abc123" :role "researcher"})
  (login-token "researcher" "abc1232")
  (fixtures/seed-db db))

(comment
  (ig-repl/go)
  (ig-repl/halt)
  (ig-repl/reset)
  (ig-repl/reset-all)
  (ig-repl/init)
  (ig-repl/clear))

(comment
  (def db (ig-state/system :database/connection))
  (def channel (ig-state/system :scrape-dataset/worker))
  (async/put! (first channel) {:doi "ds004055" :version "1.0.2"})
  (async/take! (second channel) #(pprint/pprint %))

  (def w (partial worker/dataset-preconditions db))
  (def x (partial worker/metadata-request
            (get-in config/config [:scrape-dataset/worker :openneuro-url])
            (get-in config/config [:scrape-dataset/worker :openneuro-dataset-request])))
  (def y (partial worker/snapshot-request
            (get-in config/config [:scrape-dataset/worker :openneuro-url])
            (get-in config/config [:scrape-dataset/worker :openneuro-snapshot-request])))
  (def z (partial worker/insert-dataset-db db))
  (def in (async/chan))
  (def out (async/chan))
  (async/pipeline-blocking 1 out (comp x w) in)
  (async/put! in {:doi "ds004056" :version "1.0.2"})
  (async/take! out #(pprint/pprint %))

  (async/close! in)
  (def raw-data (slurp (io/resource "raw-data.json")))
  )
