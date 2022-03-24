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
   [reitit.ring.middleware.parameters :as parameters]))

(defn routes-options [db-conn auth worker-channel]
  {:exception pretty/exception
   :data
   {:env        {:db             db-conn
                 :worker-channel worker-channel
                 :auth           auth}
    :coercion   (reitit.coercion.malli/create (assoc reitit.coercion.malli/default-options :compile mu/open-schema))
    :muuntaja   muuntaja/instance
    :middleware [parameters/parameters-middleware
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


(def dataset-routes
  ["/dataset"
   {:get  {:summary "Get the list of data-sets available to the researcher"
           :handler (fn [request]
                      (let [user-id (-> request :identity :user/id)
                            db      (-> request :env :db)]
                        {:status 200 :body (db/get-dataset-by-user-id db user-id)}))}
    :post {:summary    "Scrape the dataset of a specific url or doi number"
           :parameters {:body {:source  string?
                               :doi     string?
                               :version string?}}
           :handler    (fn [_request]
                         {:status 200 :body ""})}}])


(def research-routes
  ["/research"
   {:get {:summary "Get summary of all research the current account has"
          :handler (fn [request]
                     (let [user-id (-> request :identity :user/id)
                           db      (-> request :env :db)]
                       {:status 200 :body (db/get-research-data db user-id)}))}}])


(def api-routes
  ["/api" {:middleware [middleware/token-auth-middleware middleware/auth-middleware]}
   research-routes
   dataset-routes])


(defn routes [options]
  (ring/router
    [login-routes
     api-routes]
    options))


(defn application [db-conn auth worker-channel]
  (ring/ring-handler
    (routes (routes-options db-conn auth worker-channel))
    (ring/routes
      (ring/create-default-handler)
      (ring/redirect-trailing-slash-handler))))
