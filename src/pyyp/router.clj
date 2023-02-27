(ns pyyp.router
  (:require
   [malli.util :as mu]
   [muuntaja.core :as muuntaja]
   [pyyp.db :as db]
   [pyyp.middleware :as middleware]
   [reitit.coercion.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.muuntaja :refer [format-middleware]]
   [reitit.ring.middleware.parameters :refer [parameters-middleware]]
   [clojure.core.async :as async]))

(defn routes-options [db-conn auth worker-channel]
  {:exception pretty/exception
   :data
   {:env        {:db             db-conn
                 :worker-channel worker-channel
                 :auth           auth}
    :coercion   (reitit.coercion.malli/create (assoc reitit.coercion.malli/default-options :compile mu/open-schema))
    :muuntaja   muuntaja/instance
    :middleware [parameters-middleware
                 format-middleware
                 coercion/coerce-exceptions-middleware
                 coercion/coerce-request-middleware
                 coercion/coerce-response-middleware
                 middleware/env-middleware]}})


;; TODO save or log when user last logged in
(def login-routes
  ["/login"
   {:post
    {:summary    "Log-in to the application."
     :middleware [middleware/basic-auth-middleware middleware/auth-middleware]
     :parameters {:header {:authorization string?}}
     :handler    (fn [request]
                   (let [identity (-> request :identity)]
                     {:status 200 :body identity}))}}])


(defn get-dataset [request]
  (let [user-id (-> request :identity :user/id)
        db      (-> request :env :db)]
    {:status 200 :body (db/get-dataset-by-user-id db user-id)}))


(defn post-dataset [request]
  (let [body    (get-in request [:parameters :body])
        body    (assoc body :source (keyword (:source body)))
        channel (get-in request [:env :worker-channel])]
    (async/put! channel body)))


(def dataset-routes
  ["/dataset"
   {:get  {:summary "Get the list of data-sets available to the researcher"
           :handler get-dataset}
    :post {:summary    "Scrape the dataset of a specific url or doi number"
           :parameters {:body {:source  string?
                               :doi     string?
                               :version string?}}
           :handler    post-dataset}}])


(def research-routes
  ["/research"
   {:get {:summary "Get summary of all research the current account has"
          :handler (fn [request]
                     (let [user-id (-> request :identity :user/id)
                           db      (-> request :env :db)]
                       {:status 200 :body (db/get-research-data db user-id)}))}}])


(def api-routes
  [login-routes
   ["/api"
    {:middleware [middleware/token-auth-middleware middleware/auth-middleware]}
    research-routes
    dataset-routes]])


(defn application [db-conn auth worker-channel]
  (ring/ring-handler
    (ring/router
      login-routes
      (routes-options db-conn auth worker-channel))
    (ring/routes
      (ring/create-default-handler)
      (ring/redirect-trailing-slash-handler))))
