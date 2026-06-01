(ns acme.auth.user.web-spec
  (:require [acme.config :as config]
            [acme.spec-helper :as spec-helper]
            [acme.test-data :as test-data]
            [acme.auth.user.core :as core]
            [acme.auth.user.web :as sut]
            [c3kit.apron.legend :as legend]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.jwt :as jwt]
            [c3kit.wire.spec-helper :as wire-helper]
            [c3kit.wire.websocket :as websocket]
            [speclj.core :refer [around context describe it should-be-nil should-contain should-have-invoked should-not-have-invoked should= stub with with-stubs]]
            [speclj.stub :as stub]))

(describe "User Web Handlers"
  (with-stubs)
  (spec-helper/stub-email)
  (test-data/with-memory-kinds :user)

  (context "web-signout"

    (it "signs out"
      (let [response (sut/web-signout (sut/authorize-user {} @test-data/road-runner))]
        (wire-helper/should-redirect-to response config/host)
        (should= "You have been signed out" (flash/first-msg-text response))
        (should= :success (flash/first-msg-level response))
        (should-be-nil (core/current response))))

    (it "preserves client id"
      (let [response (-> (sut/authorize-user {} @test-data/road-runner)
                         (assoc-in [:jwt/payload :client-id] "123")
                         sut/web-signout)]
        (wire-helper/should-redirect-to response config/host)
        (should= {:client-id "123"} (:jwt/payload response)))))

  (context "websocket-open"
    (around [it]
      (with-redefs [websocket/handler (stub :websocket/handler {:return :connected})]
        (it)))

    (it "no user"
      (should-be-nil (sut/websocket-open {}))
      (should-not-have-invoked :websocket/handler))

    (it "with user"
      (let [request  (sut/authorize-user {} @test-data/road-runner)
            response (sut/websocket-open request)]
        (should= :connected response)
        (should-have-invoked :websocket/handler {:with [request {:read-csrf jwt/client-id}]}))))

  (context "ws-fetch-user-data"

    (it "no user"
      (let [response (sut/ws-fetch-user-data {})]
        (wire-helper/should-be-ws-fail response "Unable to fetch user data without sign in")))

    (it "with user"
      (let [response (sut/ws-fetch-user-data {:request (sut/authorize-user {} @test-data/road-runner)})]
        (should= :ok (:status response))
        (should-contain (legend/present! @test-data/road-runner) (:payload response))))))