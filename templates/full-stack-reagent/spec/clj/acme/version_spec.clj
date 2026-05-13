(ns acme.version-spec
  (:require [acme.version :as sut]
            [speclj.core :refer :all]))

(defn should-version [version action]
  (should= action (get-in (sut/api-get {}) [:body :client-policies version :action])))

(context "Versioning system"

  (context "api"

    (it "returns current version"
      (should= "1.0.0" (:server-version (:body (sut/api-get {})))))

    (it "client versions"
      (should-version "0.x.x" :warn)
      (should-version "1.0.0" :none))
    )
  )
