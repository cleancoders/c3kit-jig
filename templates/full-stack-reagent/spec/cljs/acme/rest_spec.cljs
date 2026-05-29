(ns acme.rest-spec
  (:require [acme.core :as core]
            [acme.rest :as sut]
            [acme.spec-helper :as helper]
            [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.api :as api]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.flashc :as flashc]
            [c3kit.wire.rest :as rest]
            [cljs-http.client :as client]
            [cljs.core.async :as async]
            [speclj.core :refer-macros [around before tags focus-describe focus-context focus-it should-not-be-nil should-have-invoked stub redefs-around with-stubs should-not should context describe should-be-nil should-be it should= should-contain should-not-be with]]))

(declare handler)
(def uri "foo.com")
(def request {})
(def response (atom nil))

(defn should-contain-flash [msg]
  (should-contain (dissoc msg :id) (map #(dissoc % :id) @flash/state)))

(describe "REST requests"

          (with-stubs)
          (helper/stub-goto!)
          (redefs-around [rest/-request! (stub :request {:invoke (fn [_ callback] (callback @response))})
                          client/get    (fn [& _] (async/chan))
                          core/goto!    (stub :goto!)])
          (around [it] (log/capture-logs (it)))
          (before (reset! api/config {})
                  (reset! flash/state {})
                  (reset! response {:status 200 :body 1}))

          (context "configuration"

            (before (sut/configure!))

            (it "invokes success handler with body of response"
                (should= 2 (sut/get! uri request inc)))

            (context "401"

              (before (swap! response assoc :status 401)
                      (sut/get! uri request inc))

              (it "flashes with sign in message"
                  (let [msg (flashc/error "Please sign in to proceed")]
                    (should-contain-flash msg)))

              (it "redirects to root page"
                  (should-have-invoked :goto! {:with ["/"]})))

            (it "403 produces flash"
                (let [msg (flashc/error "You don't have access to this resource")]
                  (swap! response assoc :status 403)
                  (sut/get! uri request inc)
                  (should-contain-flash msg)))

            (it "404 produces flash"
                (let [msg (flashc/error "Resource not found!")]
                  (swap! response assoc :status 404)
                  (sut/get! uri request inc)
                  (should-contain-flash msg)))

            (it "500 produces flash"
                (let [msg (flashc/error "Sorry, we weren't able to complete your request")]
                  (swap! response assoc :status 500)
                  (sut/get! uri request inc)
                  (should-contain-flash msg)))))
