(ns pyyp.frontend.routes
  (:require [re-frame.core :as rf]
            [reitit.coercion.malli]
            [reitit.frontend]
            [reitit.frontend.controllers :as rfc]
            [reitit.frontend.easy :as rfe]
            [pyyp.frontend.views :as views]))


(rf/reg-sub
  :current-route
  (fn [db]
    (:current-route db)))


(rf/reg-event-db
  :navigated
  (fn [db [_ new-match]]
    (let [old-match   (:current-route db)
          controllers (rfc/apply-controllers (:controllers old-match) new-match)]
      (assoc db :current-route (assoc new-match :controllers controllers)))))


(def routes
  ["/"
   ["" {:name ::home
        :view views/footer}]
   ["login" {:name ::login
             :view views/login}]
   ["research" {:name ::research
                :view views/dashboard}]])


(defn on-navigate [new-match]
  (when new-match
    (rf/dispatch [:navigated new-match])))


(def router
  (reitit.frontend/router routes {:data {:coercion reitit.coercion.malli/coercion}}))


(defn init-routes! []
  (pr "Initializing routes")
  (rfe/start! router on-navigate {:use-fragment false})
  )
