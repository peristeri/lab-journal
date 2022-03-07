(ns pyyp.router
  (:require
   [reitit.coercion.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.ring.middleware.muuntaja :refer [format-middleware]]
   [muuntaja.core :as muuntaja]
   [pyyp.handlers :as handlers]
   [pyyp.middleware :as middleware]
   ))


(defn routes-options [db-conn auth worker-channel]
  {:exception pretty/exception
   :data
   {:env        {:db             db-conn
                 :worker-channel worker-channel
                 :auth           auth}
    :coercion   (reitit.coercion.malli/create reitit.coercion.malli/default-options)
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
     :parameters {:header [:map [:authorization string?]]}
     :handler    (fn [request]
                   (let [identity (-> request :identity)]
                     {:status 200 :body identity}))}}])


(defn research-summary [username]
  username)

(def research-routes
  ["/research"
   {:get
    {:summary "Get summary of all research the current account has"
     :handler (fn [request]
                (let [username (-> request :identity :username)]
                  (research-summary username)
                  ))}}])

(defn routes [options]
  (ring/router
    [
     login-routes
     ["/api" {:middleware [middleware/token-auth-middleware
                           middleware/auth-middleware]}
      research-routes
      ["/research_" {:put
                     {:summary    "Create a new research project"
                      :handler    handlers/create-research
                      :parameters {:body
                                   [:map
                                    [:leader          string?]
                                    [:title           string?]
                                    [:specification   map?]
                                    [:data_repository string?]
                                    [:version         string?]]}}}]]]
    options))


(defn application [db-conn auth worker-channel]
  (ring/ring-handler
    (routes (routes-options db-conn auth worker-channel))
    (ring/routes
      (ring/create-default-handler)
      (ring/redirect-trailing-slash-handler))))
