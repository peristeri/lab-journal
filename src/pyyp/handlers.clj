(ns pyyp.handlers
  (:require [pyyp.db :as db]
            [buddy.hashers :refer [check]]
            [buddy.sign.jwt :as jwt]
            [ring.util.response :as response]))


(defn authenticated? [account password]
  (when (and account
             (check password (:account/password account)))
    account))


(defn account->response [account secret]
  (let [sanitized (dissoc account :account/password)
        token     (jwt/sign sanitized secret)]
    (assoc sanitized :account/token token)))


(defn login [{:keys [db body-params jwt-secret]}]
  (let [{:keys [username password]} body-params
        account                     (db/account-by-username db username)
        response                    (when (and account (authenticated? account password))
                                      (account->response account jwt-secret))]
    (if response
      (response/response response)
      {:status 403 :headers {} :body "Invalid authentication"})))


(defn create-research [{:keys [db body-params]}]
  (let [leader   (->> body-params
                      :leader
                      (db/account-by-username db)
                      :account/id)
        request  (when leader
                   (assoc body-params :leader leader))
        response (when request
                   (db/create-research! db request))]
    (if response
      (response/response {:status "success" :research-id (:id response)})
      (response/bad-request {:status "failed" :reason "Invalid request"}))))
