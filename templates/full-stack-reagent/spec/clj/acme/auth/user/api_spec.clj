(ns acme.auth.user.api-spec
  (:require [acme.spec-helper :as spec-helper]
            [acme.test-data :as test-data]
            [acme.auth.user.api :as sut]
            [acme.auth.user.core :as core]
            [c3kit.apron.legend :as legend]
            [c3kit.wire.spec-helper :as wire-helper :refer [should-be-ajax-fail should-be-ajax-ok]]
            [speclj.core :refer [context describe it should-contain should= with-stubs]]))

(describe "User REST API Handlers"
  (with-stubs)
  (spec-helper/stub-email)
  (test-data/with-memory-kinds :user)

  (context "api-signin"
    (spec-helper/with-fast-password-hash)

    (it "invalid credentials"
      (let [response (sut/api-signin {:body {:email "bad@email.com" :password "wrong"}})]
        (should= 400 (:status response))
        (should-contain :errors (:body response))))

    (it "success"
      (let [response (sut/api-signin {:body {:email (:email @test-data/road-runner)
                                             :password "meep-meep"}})]
        (should= 200 (:status response))
        (should= (legend/present! @test-data/road-runner) (:body response)))))

  (context "api-signup"
    (spec-helper/with-fast-password-hash)

    (it "missing fields"
      (let [response (sut/api-signup {:body {}})]
        (should= 400 (:status response))
        (should-contain :errors (:body response))))

    (it "success"
      (let [response (sut/api-signup {:body {:email "new@user.com"
                                             :password "secret123"
                                             :confirm-password "secret123"}})]
        (should= 200 (:status response))
        (should= "new@user.com" (-> response :body :email))))))