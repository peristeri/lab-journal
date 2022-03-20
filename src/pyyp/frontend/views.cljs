(ns pyyp.frontend.views
  (:require [reagent.core :as reagent]
            [re-frame.core :refer [subscribe dispatch]]))


(defn errors-list
  [errors]
  [:ul.error-messages
   (for [[k [v]] errors]
     ^{:key k} [:li (str (name k) " " v)])])


(defn login []
  (let [default     {:email "" :password ""}
        credentials (reagent/atom default)]
    (fn []
      (let [{:keys [email password]} @credentials
            loading                  @(subscribe [:loading])
            errors                   @(subscribe [:errors])
            login-user               (fn [event credentials]
                                        (.preventDefault event)
                                        (dispatch [:login credentials]))]
        [:div.auth-page
         [:div.container.page
          [:div.row
           [:div.col-md-6.offset-md-3.col-xs-12
            [:h1.text-xs-center "Sign in"]
            (when (:login errors)
              [errors-list (:login errors)])
            [:form {:on-submit #(login-user % @credentials)}
             [:fieldset.form-group
              [:input.form-control.form-control-lg
               {:type        "text"
                :placeholder "Email"
                :value       email
                :on-change   #(swap! credentials assoc :email (-> % .-target .-value))
                :disabled    (:login loading)}]]

             [:fieldset.form-group
              [:input.form-control.form-control-lg
               {:type        "password"
                :placeholder "Password"
                :value       password
                :on-change   #(swap! credentials assoc :password (-> % .-target .-value))
                :disabled    (:login loading)}]]
             [:button.btn.btn-lg.btn-primary.pull-xs-right {:class (when (:login loading) "disabled")} "Sign in"]]]]]]))))


(defn dashboard []
  (let [profile    @(subscribe [:profile])
        researches @(subscribe [:researches])]
    [:div.home-page
     [:div.container
      [:h1 "research labs"]
      [:p (:account/username profile)]
      [:p (:research/title (first researches))]]]))


(defn header []
  [:nav.navbar
   [:a "Lab Journal"]
   [:ul
    [:li [:a "Profile"]]
    [:li [:a "Search"]]]])


(defn footer []
  [:footer [:div.container [:span.attribution "A learning project from Py-yP"]]])


(defn active-page []
  (let [current-route @(subscribe [:current-route])]
    [:div
     (when current-route
       [(-> current-route :data :view)])]))


(defn view []
  [:div#root
   [header]
   [active-page]
   [footer]])
