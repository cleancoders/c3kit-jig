(ns acme.security.csp-spec
  (:require [acme.security.csp :as sut]
            [speclj.core :refer [describe it should should-be-nil should=]]))

(describe "policy->header-value"

  (it "joins a single directive"
    (should= "default-src 'self'"
             (sut/policy->header-value {:default-src ["'self'"]})))

  (it "joins multiple directives with semicolons"
    (should= "default-src 'self'; img-src 'self' data:"
             (sut/policy->header-value {:default-src ["'self'"]
                                        :img-src     ["'self'" "data:"]})))

  (it "keeps report-uri as a single value"
    (let [out (sut/policy->header-value {:report-uri "/api/v1/csp-report"})]
      (should (re-find #"report-uri /api/v1/csp-report" out)))))

(describe "wrap-csp"

  (it "adds Content-Security-Policy-Report-Only by default"
    (let [handler (sut/wrap-csp (fn [_] {:status 200 :headers {} :body ""})
                                {:policy {:default-src ["'self'"]}})
          resp    (handler {})]
      (should= "default-src 'self'"
               (get-in resp [:headers "Content-Security-Policy-Report-Only"]))
      (should-be-nil (get-in resp [:headers "Content-Security-Policy"]))))

  (it "adds Content-Security-Policy when :enforce? is true"
    (let [handler (sut/wrap-csp (fn [_] {:status 200 :headers {} :body ""})
                                {:enforce? true :policy {:default-src ["'self'"]}})
          resp    (handler {})]
      (should= "default-src 'self'" (get-in resp [:headers "Content-Security-Policy"])))))

(describe "csp-report-handler"

  (it "returns 204"
    (should= 204 (:status (sut/csp-report-handler {:body ""})))))
