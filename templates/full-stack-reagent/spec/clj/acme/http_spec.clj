(ns acme.http-spec
  (:require [acme.http :as sut]
            [speclj.core :refer [describe it should-be-nil should=]]))

(describe "wrap-security-headers"

  (it "sets X-Frame-Options DENY"
    (let [handler  (sut/wrap-security-headers (fn [_] {:status 200 :headers {} :body ""}))
          response (handler {})]
      (should= "DENY" (get-in response [:headers "X-Frame-Options"]))))

  (it "sets X-Content-Type-Options nosniff"
    (let [handler  (sut/wrap-security-headers (fn [_] {:status 200 :headers {} :body ""}))
          response (handler {})]
      (should= "nosniff" (get-in response [:headers "X-Content-Type-Options"]))))

  (it "sets Referrer-Policy"
    (let [handler  (sut/wrap-security-headers (fn [_] {:status 200 :headers {} :body ""}))
          response (handler {})]
      (should= "strict-origin-when-cross-origin" (get-in response [:headers "Referrer-Policy"]))))

  (it "sets Strict-Transport-Security"
    (let [handler  (sut/wrap-security-headers (fn [_] {:status 200 :headers {} :body ""}))
          response (handler {})]
      (should= "max-age=31536000; includeSubDomains" (get-in response [:headers "Strict-Transport-Security"]))))

  (it "sets X-XSS-Protection"
    (let [handler  (sut/wrap-security-headers (fn [_] {:status 200 :headers {} :body ""}))
          response (handler {})]
      (should= "1; mode=block" (get-in response [:headers "X-XSS-Protection"]))))

  (it "response-specific header wins over default"
    (let [handler  (sut/wrap-security-headers
                    (fn [_] {:status 200 :headers {"X-Frame-Options" "SAMEORIGIN"} :body ""}))
          response (handler {})]
      (should= "SAMEORIGIN" (get-in response [:headers "X-Frame-Options"]))))

  (it "passes through nil response"
    (let [handler (sut/wrap-security-headers (fn [_] nil))]
      (should-be-nil (handler {})))))
