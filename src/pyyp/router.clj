(ns pyyp.router
  (:require [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.backends :refer [jws]]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [reitit.coercion.malli]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [ring.middleware.params :refer [wrap-params]]
            [reitit.ring.middleware.muuntaja :refer [format-middleware]]
            [muuntaja.core :as muuntaja]
            [malli.util]
            [pyyp.handlers :as handlers]
            [pyyp.db :as db]))


(def inject-env-middleware
  {:name    ::db
   :compile (fn [{:keys [env]} _]
              (fn [handler]
                (fn [request]
                  (handler (-> request
                               (assoc :db (:db env))
                               (assoc :jwt-secret (:jwt-secret env)))))))})


(def wrap-check-authorization
  {:name :authorization
   :wrap (fn [handler]
           (fn [request]
             (if (authenticated? request)
               (let [username (get-in request [:identity :account/username])
                     id       (get-in request [:identity :account/id])
                     db       (:db request)
                     account  (db/verify-account db username id)]
                 (if account
                   (handler request)
                   (throw-unauthorized)
                   )
                 )
               (throw-unauthorized)
               ))
           )})


(defn routes-options [db-conn jwt-secret]
  {

   :exception pretty/exception
   :data      {:env        {:db db-conn :jwt-secret jwt-secret}
               :coercion   (reitit.coercion.malli/create
                             {:compile malli.util/closed-schema
                              })
               :muuntaja   muuntaja/instance
               :middleware [
                            wrap-params
                            format-middleware
                            [wrap-authentication (jws {:secret jwt-secret :token-name "Bearer"})]
                            [wrap-authorization (jws {:secret jwt-secret})]
                            coercion/coerce-exceptions-middleware
                            coercion/coerce-request-middleware
                            coercion/coerce-response-middleware
                            inject-env-middleware
                            ]}})


(defn placeholder-handler [request]
  {:status 200 :body (:uri request)})


(defn routes [options]
  (ring/router
    [["/ping" {:get {:handler (fn [_] {:status 200 :body "pong"})}}]
     ["/login" {:post       {:handler handlers/login}
                :parameters {:body {:username string? :password string?}}}]
     ["/api" {:middleware [wrap-check-authorization]}
      ["/research" {:put     {:handler handlers/create-research}
                    :handler {:body
                              {:username      string?
                               :title         string?
                               :specification string?
                               :source        string?
                               :version       string?}}}]]]
    options
    ))

(defn application [db-conn jwt-secret]
  (ring/ring-handler
    (routes (routes-options db-conn jwt-secret))
    (ring/routes
      (ring/create-default-handler)
      (ring/redirect-trailing-slash-handler))))
