(ns pyyp.middleware
  (:require
   [buddy.auth :as auth]
   [buddy.auth.backends :as backends]
   [buddy.auth.middleware :as middleware]
   [buddy.hashers :as hasher]
   [buddy.sign.jwt :as jwt]
   [tick.core :as tick]
   [pyyp.db :as db]))


(def env-middleware
  {:name    ::db
   :compile (fn [{:keys [env]} _]
              (fn [handler]
                (fn [request]
                  (handler (assoc request :env env)))))})


(defn registered? [user]
  (and user (:account/is_active user)))


(def unregistered? (complement registered?))


;; TODO change the username to email once we have it in the db
(defn create-jwt-token [{:keys [jwt-secret jwt-opts jwt-token-expire-secs]} account]
  (let [now    (tick/now)
        expire (tick/>> now (tick/new-duration jwt-token-expire-secs :seconds))
        claims {:iss        "pyyp.io"
                :iat        now
                :exp        expire
                :user/id    (:account/id account)
                :user/email (:account/username account)}]
    (jwt/sign claims jwt-secret jwt-opts)))


(defn basic-auth [{{:keys [db auth]} :env} {:keys [username password]}]
  (let [user (db/account-by-username db username)]
    (cond
      (unregistered? user)
      false
      (hasher/check password (:account/password user))
      (-> user
          (assoc :account/token (create-jwt-token auth user))
          (dissoc :account/password :account/id))
      :else
      false)))


(defn basic-auth-middleware [handler]
  (let [auth-backend (backends/basic {:authfn (partial basic-auth)})]
    (middleware/wrap-authentication handler auth-backend)))


(defn token-auth-middleware [handler]
  (fn [request]
    (let [jwt-secret    (-> request :env :auth :jwt-secret)
          jwt-opts      (-> request :env :auth :jwt-opts)
          token-backend (backends/jws {:secret     jwt-secret
                                       :options    jwt-opts
                                       :token-name "Bearer"})]
      ((middleware/wrap-authentication handler token-backend) request))))


(defn auth-middleware [handler]
  (fn [request]
    (if (auth/authenticated? request)
      (handler request)
      {:status 401 :body {:error "Unauthorized"
                          }})))
