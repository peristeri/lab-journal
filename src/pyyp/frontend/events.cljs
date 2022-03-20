(ns pyyp.frontend.events
  (:require [re-frame.core :as rf]))


(rf/reg-event-fx :init
                 (fn [_]
                   {:db {:current-page :home}}))
