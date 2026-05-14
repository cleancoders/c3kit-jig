(ns acme.auth.user.ajax-spec
  (:require [acme.config :as config]
            [acme.spec-helper :as spec-helper]
            [acme.test-data :as test-data]
            [acme.auth.user.ajax :as sut]
            [acme.auth.user.core :as core]
            [c3kit.apron.log :as log]
            [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.spec-helper :as wire-helper :refer [should-be-ajax-fail should-be-ajax-ok]]
            [speclj.core :refer :all]))

(describe "User AJAX Handlers"
  (with-stubs)
  (spec-helper/stub-email)
  (test-data/with-memory-kinds :user)

  (context "ajax-csrf-token"

    (it "returns the csrf-token"
      (let [response (sut/ajax-csrf-token {:jwt/payload {:client-id "abc123"}})]
        (should= 200 (:status response))
        (should= :ok (ajax/status response))
        (should= "abc123" (:ws-csrf-token (ajax/payload response)))
        (should= "abc123" (:anti-forgery-token (ajax/payload response)))))

    (it "puts something in the session to make sure one exists"
      (let [response (sut/ajax-csrf-token {:jwt/payload {:foo :bar}})]
        (should= :bar (-> response :jwt/payload :foo))))
    )

  (context "ajax-signin"
    (spec-helper/with-fast-password-hash)

    (it "no email"
      (let [response (sut/ajax-signin {:params {}})]
        (should-be-ajax-fail response "Invalid username or password")
        (should-contain :errors (ajax/payload response))))

    (it "not a current user"
      (let [response (sut/ajax-signin {:params {:email "not-a-user@example.com" :password "password"}})]
        (should-be-ajax-fail response "Invalid username or password")
        (should-contain :errors (ajax/payload response))))

    (it "inactive user"
      (db/tx @test-data/coyote :password nil)
      (let [response (sut/ajax-signin {:params {:email "coyote@example.com" :password "password"}})]
        (should-be-ajax-fail response "Invalid username or password")
        (should-contain :errors (ajax/payload response))))

    (it "no password"
      (let [response (sut/ajax-signin {:params {:email "blah"}})]
        (should-be-ajax-fail response "Invalid username or password")
        (should-contain :errors (ajax/payload response))))

    (it "incorrect password"
      (let [response (sut/ajax-signin {:params {:email "road-runner@example.com" :password "invalid"}})]
        (should-be-ajax-fail response "Invalid username or password")
        (should-contain :errors (ajax/payload response))))

    (it "success"
      (let [response (sut/ajax-signin {:params {:email (:email @test-data/road-runner)
                                                :password "meep-meep"}})]
        (should= :ok (ajax/status response))
        (should= "Welcome to Acme" (ajax/first-flash-text response))))
    )

  (context "ajax-forgot-password"

    (it "missing email"
      (let [response (sut/ajax-forgot-password {:params {:email "\r\n\t "}})]
        (should-be-ajax-fail response "Missing email address.")))

    (it "provides an email that does not exist"
      (log/with-level :error
        (let [response (sut/ajax-forgot-password {:params {:email "fake@mail.com"}})]
          (should-be-ajax-ok response "Please check your email to recover your account.")
          (should-be-nil (spec-helper/last-email)))))

    (it "provides an email that exists"
      (let [response (sut/ajax-forgot-password {:params {:email (:email @test-data/coyote)}})]
        (should-be-ajax-ok response "Please check your email to recover your account.")
        (should (uuid? (schema/->uuid (:recovery-token @test-data/coyote))))
        (should= {:to      "coyote@example.com"
                  :from    config/admin-email
                  :subject "Acme: Recover your account"
                  :text    (str "Click this link to recover your Acme account:\n\n" config/host "/recover-password/" (:recovery-token @test-data/coyote))}
                 (spec-helper/last-email))))
    )

  (context "ajax-reset-password"

    (it "missing recovery token"
      (let [response (sut/ajax-reset-password {:params {:recovery-token "\r\n\t " :password "blah"}})]
        (should-be-ajax-fail response "Missing recovery token")))

    (it "missing password token"
      (let [response (sut/ajax-reset-password {:params {:recovery-token "blah" :password "\r\n\t "}})]
        (should-be-ajax-fail response "Missing password")))

    (it "invalid recovery token fails silently"
      (let [token    (str (random-uuid))
            response (sut/ajax-reset-password {:params {:recovery-token token :password "bar"}})]
        (should= :ok (ajax/status response))))

    (it "valid recovery token resets password"
      (let [token (random-uuid)]
        (db/tx @test-data/coyote :recovery-token token)
        (let [response (sut/ajax-reset-password {:params {:recovery-token (str token) :password "bar"}})]
          (should= :ok (ajax/status response))
          (should-be-nil (:recovery-token @test-data/coyote))
          (should= (core/hash-password "bar") (:password @test-data/coyote)))))
    )
  )
