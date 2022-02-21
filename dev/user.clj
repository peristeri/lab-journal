(ns user
  (:require [integrant.repl :as ig-repl]
            [integrant.repl.state :as ig-state]
            [pyyp.config :as config]
            [pyyp.db :as db]
            [clj-http.client :as client]
            ))

(ig-repl/set-prep! (fn [] config/config))

(defn build-db []
  ;; Start a new DB instance with seed data
  (let [db (ig-state/system :database/connection)]
    (db/create-tables db)



    #_(db/verify-account db "researcher" "ef273914-17d8-4718-bf0a-31a85883da0c")
    #_(db/account-by-username db "researcher"))
  )

(defn seed-db []
  (let [db (ig-state/system :database/connection)]
    #_(db/create-account db
                         {:username "researcher"
                          :password "abc123"
                          :role     "researcher"})
    #_(db/create-account db
                         {:username "worker-bot"
                          :password "abc123"
                          :role     "worker"})
    (db/create-reseach db
                       {:username        "researcher"
                        :title           "Sample research"
                        :specification   {:resolution "20dpi" :dimensions "20x30x40"}
                        :data_repository "git://neuro-lab/sample/data/mri"
                        :version         "5c446b"})))

(defn play-with-routes []
  (let [app (ig-state/system :backend/application)]
    (app {:request-method :get :uri "/ping"})
    (app {:request-method :post :body-params {"username" "researcher" "password" "abc123"} :uri "/login"})
    (app {:request-method :post :body-params {"username" "researcher" "password_" "bob"} :uri "/login"})
    ))

(comment
  (ig-repl/go)
  (ig-repl/halt)
  (ig-repl/reset)
  (ig-repl/reset-all)
  ;; seed a demo user and a testing bot
  (ig-repl/init)
  (def application (ig-state/system :backend/application))

  (build-db)
  (play-with-routes)

  ;; don't know why this is failing...
  (client/get "http://localhost:3000/ping")
  (client/post "http://localhost:3000/login"
               {:debug        true
                :debug-body   true
                :body         "{\"username\": \"researcher\", \"password\": \"abc123\"}"
                :content-type :json})
  (client/put "http://localhost:3000/api/research"
              {:body ""})

  )
