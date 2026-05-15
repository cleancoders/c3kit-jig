(ns acme.auth.user.apple
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.rest :as rest]
            [acme.auth.user.core :as auth]))

(def pkey-host "https://appleid.apple.com/auth/keys")

(defmethod auth/pkeys :apple [_impl]
  (try
    (->> (rest/get! pkey-host nil)
         :body
         utilc/<-json-kw
         :keys
         (reduce (fn [m k] (assoc m (:kid k) k)) {}))
    (catch Exception _
      (throw (ex-info (str "Could not fetch pkeys from " pkey-host) nil)))))
