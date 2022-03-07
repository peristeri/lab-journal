(ns user
  (:require [integrant.repl :as ig-repl]
            [integrant.repl.state :as ig-state]
            [pyyp.config :as config]
            [pyyp.db :as db]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [fixtures]
            [ring.util.response]
            ))

(ig-repl/set-prep! (fn [] config/config))
(def db (ig-state/system :database/connection))

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

(defn start-research-project []
  ;; identify the leader a project, for this example it is the same as the current user
  (let [username "researcher"
        password "abc123"
        token    (login-token username password)]
    token
    (create-research username "More more more" token)))

(comment
  (ig-repl/go)
  (ig-repl/halt)
  (ig-repl/reset)
  (ig-repl/reset-all)
  (ig-repl/init)
  (ig-repl/clear)
  (db/create-tables db)
  (fixtures/seed-accounts db 5)
  (fixtures/seed-researches db 50)
  (fixtures/seed-datasets db 1 300)
  (fixtures/seed-experiments db 10)

  (login-token "researcher" "abc123")
  (db/create-account! db {:username "researcher" :password "abc123" :role "researcher"})
  (client/post "http://localhost:3000/login" {:basic-auth ["researcher" "abc123"]})

  (start-research-project)
  )
