(ns user
  (:require [integrant.repl :as ig-repl]
            [integrant.repl.state :as ig-state]
            [pyyp.config :as config]
            [pyyp.db :as db]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            ))

(ig-repl/set-prep! (fn [] config/config))

(defn build-db []
  (let [db (ig-state/system :database/connection)]
    (db/create-tables db)))

(defn seed-db []
  (let [db (ig-state/system :database/connection)]
    (db/create-account db {:username "researcher" :password "abc123" :role "researcher"})
    (db/create-account db {:username "worker-bot" :password "abc123" :role "worker"})))

(defn play-with-routes []
  (let [app (ig-state/system :backend/application)]
    (app {:request-method :get :uri "/ping"})
    (app {:request-method :post :body-params {"username" "researcher" "password" "abc123"} :uri "/login"})
    (app {:request-method :post :body-params {"username" "researcher" "password_" "bob"} :uri "/login"})))

(defn login-token [username password]
  (-> (client/post "http://localhost:3000/login"
                   {:body         (str "{\"username\": \"" username "\", \"password\": \"" password "\"}")
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
  (->> (login-token "researcher" "abc123")
       (create-research "researcher" "more sample research")
       )
  (build-db)
  (seed-db)
  (start-research-project)

  (def application (ig-state/system :backend/application))
  )
