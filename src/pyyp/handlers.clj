(ns pyyp.handlers
  (:require [pyyp.db :as db]
            [buddy.hashers :refer [check]]
            [buddy.sign.jwt :as jwt]))

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
      {:status 200 :body response}
      {:status 403 :body "Invalid authentication"}
      )
    )
  )

(defn create-research [{:keys [db identity body-params] :as request}]
  (let [username    (:account/username identity)
        user-id     (:account/id identity)
        account     (db/verify-account db username user-id)
        new-reseach (assoc body-params :username username)
        response    (when account (db/create-reseach db new-reseach))
        ]
    (if response
      {:status 200 :body {:status "success" :research-id (:id response)}}
      {:status 400 :body {:status "failed" :reason "Invalid research"}})
    ))
