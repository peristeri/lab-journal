(ns pyyp.frontend.core
  (:require
   [reagent.dom :as dom]
   [re-frame.core :as rf]
   [pyyp.frontend.events]
   [pyyp.frontend.subscriptions]
   [pyyp.frontend.views :refer [view]]
   [pyyp.frontend.routes :as routes]))


(defn ^:dev/after-load start []
  (rf/clear-subscription-cache!)
  (routes/init-routes!)
  (dom/render [view]
              (.getElementById js/document "app")))

(defn ^:export init []
  ;; printing to the console for development only
  (enable-console-print!)
  (start)
  )
