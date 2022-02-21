(ns pyyp.router
  (:require [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [ring.middleware.params :refer [wrap-params]]
            [reitit.ring.middleware.muuntaja :refer [format-middleware]]
            [muuntaja.core :as muuntaja]
            [pyyp.handlers :as handlers]))

(def inject-env-middleware
  {:name    ::db
   :compile (fn [{:keys [env]} _]
              (fn [handler]
                (fn [request]
                  (handler (-> request
                               (assoc :db (:db env))
                               (assoc :jwt-secret (:jwt-secret env)))))))})

(defn routes-options [db-conn jwt-secret]
  {
   :data {:env        {:db db-conn :jwt-secret jwt-secret}
          :muuntaja   muuntaja/instance
          :middleware [
                       wrap-params
                       format-middleware
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
     ["/login" {:post {:handler handlers/login}}]]
    options
    ))

(defn application [db-conn jwt-secret]
(ring/ring-handler
  (routes (routes-options db-conn jwt-secret))
  (ring/routes
    (ring/create-default-handler)
    (ring/redirect-trailing-slash-handler))))
