(ns pyyp.frontend.subscriptions
  (:require [re-frame.core :refer [reg-sub]]))


(reg-sub :current-page
         (fn [db _]
           (:current-page db)))

(reg-sub :profile
         (fn [db _]
           (:profile db)))

(reg-sub :errors
         (fn [db _]
           (:errors db)))

(reg-sub :loading
         (fn [db _]
           (:loading db)))

(reg-sub :researches
         (fn [db _]
           (:researches db)))
