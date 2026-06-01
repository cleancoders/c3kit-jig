(ns acme.layouts-spec
  (:require [acme.config :as config]
            [acme.layouts :as sut]
            [acme.test-data :as test-data]
            [c3kit.wire.api :as api]
            [c3kit.wire.flash :as flash]
            [speclj.core :refer [describe it should should-be-nil should-have-invoked should-not should= stub with with-stubs]]
            [speclj.stub :as stub]))

(describe "layouts"
  (with-stubs)
  (test-data/with-memory-schema)

  (it "rich client handler includes flash messages"
    (with-redefs [sut/rich-client (stub :layout/rich-client)]
      (sut/web-rich-client (flash/warn {:flash {:foo :bar}} "Hello"))
      (should-have-invoked :layout/rich-client)
      (should= "Hello" (-> :layout/rich-client stub/last-invocation-of first :flash first :text))))

  (it "rich client payload config"
    (let [{:keys [acme-root environment host api-version ws-csrf-token anti-forgery-token google-client-id]}
          (->> {:jwt/payload {:client-id "abc123"}}
               sut/build-rich-client-payload
               :config)]
      (should= "abc123" anti-forgery-token)
      (should= "abc123" ws-csrf-token)
      (should= (api/version) api-version)
      (should= "development" environment)
      (should= (-> config/env :google-oauth :client-id) google-client-id)
      (should= (-> config/env :cleancoders-auth :url-root) acme-root)
      (should= config/host host)))

  (describe "rich-client :seo/preview slot"

    (it "default (no preview) renders the placeholder text"
      (let [response (sut/web-rich-client {})
            body     (:body response)]
        (should (re-find #"Your page is loading\.\.\." body))))

    (it "preview HTML is rendered into #app-root"
      (let [response (sut/web-rich-client {} {:seo/preview "<h1>HELLO PRERENDER</h1>"})
            body     (:body response)]
        (should (re-find #"<h1>HELLO PRERENDER</h1>" body))
        (should-not (re-find #"Your page is loading" body))))))

;; @c3kit/feature :ssr {
(describe "prerendered-html + web-prerendered"
  (test-data/with-memory-schema)

  (it "prerendered-html returns nil when file missing"
    (should-be-nil (sut/prerendered-html :no-such-page)))

  (it "web-prerendered returns rich-client with :seo/preview when prerender file exists"
    (with-redefs [sut/prerendered-html (constantly "<h1>FROM PRERENDER</h1>")]
      (let [handler  (sut/web-prerendered :home)
            response (handler {})]
        (should= 200 (:status response))
        (should (re-find #"FROM PRERENDER" (:body response))))))

  (it "web-prerendered falls back to rich-client placeholder when no file"
    (with-redefs [sut/prerendered-html (constantly nil)]
      (let [handler  (sut/web-prerendered :home)
            response (handler {})]
        (should= 200 (:status response))
        (should (re-find #"Your page is loading" (:body response)))))))

(describe "web-prerendered honors Accept: text/markdown"
  (test-data/with-memory-schema)

  (it "returns markdown when file exists and Accept matches"
    (with-redefs [sut/prerendered-html     (constantly "<h1>X</h1>")
                  sut/prerendered-markdown (constantly "# X")]
      (let [response ((sut/web-prerendered :home) {:headers {"accept" "text/markdown"}})]
        (should= 200 (:status response))
        (should= "text/markdown; charset=utf-8" (get-in response [:headers "Content-Type"]))
        (should= "# X" (:body response)))))

  (it "falls back to HTML when markdown file missing"
    (with-redefs [sut/prerendered-html     (constantly "<h1>X</h1>")
                  sut/prerendered-markdown (constantly nil)]
      (let [response ((sut/web-prerendered :home) {:headers {"accept" "text/markdown"}})]
        (should= 200 (:status response))
        (should (re-find #"<h1>X</h1>" (:body response)))))))
;; @c3kit/feature :ssr }
