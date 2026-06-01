(ns acme.routes-spec
  (:require [acme.layouts]
            ;; @c3kit/feature :content = [acme.content.core]
            [acme.routes :as routes]
            [acme.sandbox.core]
            [acme.spec-helper]
            ;; @c3kit/feature :auth {
            [acme.test-data :as test-data]
            [acme.auth.user.ajax]
            [acme.auth.user.api]
            [acme.auth.user.web]
            ;; @c3kit/feature :auth }
            [c3kit.wire.api]
            [c3kit.wire.spec-helper :as wire-helper]
            [c3kit.wire.websocket :as ws]
            [speclj.core :refer [after around before describe it should should-be-nil should-have-invoked should-not= should= stub with-stubs]]
            [speclj.stub :as stub]))

(def args (atom :none))

;; MDM - Macros are used here to preserve line number when specs fail

(defmacro check-route [path method handler]
  `(let [stub-key# ~(keyword handler)]
     (require '~(symbol (namespace handler)))
     (with-redefs [~handler (stub stub-key#)]
       (routes/handler {:uri ~path :request-method ~method})
       (should-have-invoked stub-key#)
       (reset! args (stub/first-invocation-of stub-key#)))))

(defmacro test-route [path method handler & body]
  `(it ~path
     (check-route ~path ~method ~handler)
     ~@body))

(defmacro test-redirect [path method dest]
  `(it (str ~path " -> " ~dest)
     (let [response# (routes/handler {:uri ~path :request-method ~method})]
       (wire-helper/should-redirect-to response# ~dest))))

(defmacro test-webs [id sym]
  `(it (str "remote " ~id " -> " '~sym)
     (let [action# (ws/resolve-handler ~id)]
       (should-not= nil action#)
       (should= '~sym (.toSymbol action#)))))

(describe "Routes"
  (with-stubs)
  ;; @c3kit/feature :auth {
  (test-data/with-memory-kinds :user)
  ;; @c3kit/feature :auth }
  (before (reset! args :none))
  ;; @c3kit/feature :content {
  (before (acme.content.core/load!))
  ;; @c3kit/feature :content }
  (around [it] (with-redefs [c3kit.wire.api/version (constantly "fake-api-version")] (it)))

  ; Please keep these specs sorted alphabetically

  ;; web routes
  ;; @c3kit/feature :ssr {
  (it "GET / serves prerendered home"
    (with-redefs [acme.layouts/prerendered-html (constantly "<h1>FROM PRERENDER</h1>")]
      (let [response (routes/handler {:uri "/" :request-method :get})]
        (should= 200 (:status response))
        (should (re-find #"FROM PRERENDER" (:body response))))))
  ;; @c3kit/feature :ssr }
  ;; @c3kit/feature :auth {
  (test-route "/apple/oauth" :post acme.auth.user.web/web-apple-oauth-login)
  (test-route "/forgot-password" :get acme.layouts/web-rich-client)
  (test-route "/google/oauth" :post acme.auth.user.web/web-google-oauth-login)
  (test-route "/recover-password/foo" :get acme.layouts/web-rich-client)
  ;; @c3kit/feature :auth }
  (test-route "/sandbox/example-page" :get acme.sandbox.core/handler)
  ;; @c3kit/feature :auth {
  (test-route "/signout" :any acme.auth.user.web/web-signout)
  (test-route "/user/websocket" :any acme.auth.user.web/websocket-open)
  ;; @c3kit/feature :auth }

  ;; ajax routes
  ;; @c3kit/feature :auth {
  (test-route "/ajax/forgot-password" :post acme.auth.user.ajax/ajax-forgot-password)
  (test-route "/ajax/recover-password" :post acme.auth.user.ajax/ajax-reset-password)
  ;; @c3kit/feature :auth }
  (test-route "/ajax/spinner" :get acme.routes/spinner)
  ;; @c3kit/feature :auth {
  (test-route "/ajax/user/csrf-token" :get acme.auth.user.ajax/ajax-csrf-token)
  (test-route "/ajax/user/signin" :post acme.auth.user.ajax/ajax-signin)
  (test-route "/ajax/user/signup" :post acme.auth.user.ajax/ajax-signup)
  ;; @c3kit/feature :auth }

  ;; api routes
  ;; @c3kit/feature :content {
  (test-route "/api/v1/content/blog/2026-05-12-hello-world" :get acme.content.core/api-fetch-post)
  ;; @c3kit/feature :content }
  ;; @c3kit/feature :auth {
  (test-route "/api/user/forgot-password" :post acme.auth.user.api/api-forgot-password)
  (test-route "/api/user/reset-password/some-token" :post acme.auth.user.api/api-reset-password)
  (test-route "/api/user/signin" :post acme.auth.user.api/api-signin)
  (test-route "/api/user/signup" :post acme.auth.user.api/api-signup)
  (test-route "/api/user/social/google" :post acme.auth.user.api/api-social-auth)
  (test-route "/api/user/social/apple" :post acme.auth.user.api/api-social-auth)
  ;; @c3kit/feature :auth }

  ;; websocket handlers
  ;; @c3kit/feature :auth {
  (test-webs :user/fetch-data acme.auth.user.web/ws-fetch-user-data)
  ;; @c3kit/feature :auth }

  ;; content auto-routes
  ;; @c3kit/feature :content {
  (it "GET /blog reaches content/web-list"
    (let [response (routes/handler {:uri "/blog" :request-method :get})]
      (should= 200 (:status response))
      (should (re-find #"Hello, world" (:body response)))))

  (it "GET /blog/2026-05-12-hello-world reaches content/web-post"
    (let [response (routes/handler {:uri "/blog/2026-05-12-hello-world" :request-method :get :params {:permalink "2026-05-12-hello-world"}})]
      (should= 200 (:status response))
      (should (re-find #"Hello, world" (:body response)))))
  ;; @c3kit/feature :content }

  (it "not-found global - nil - handled by http"
    (let [response (routes/handler {:uri "/blah" :request-method :get})]
      (should-be-nil response)))

  (it "spinner"
    (with-redefs [routes/sleep-for-10 (stub :sleep)]
      (wire-helper/should-be-ajax-ok (routes/spinner :blah) nil)
      (should-have-invoked :sleep)))

  ;; @c3kit/feature :content {
  (it "content catch-all is declared after explicit frontend routes"
    (let [src          (slurp "src/cljs/acme/routes.cljs")
          sandbox-pos  (.indexOf src "/sandbox/")
          catchall-pos (.indexOf src "/:content-type")]
      (should (pos? catchall-pos))
      (should (< sandbox-pos catchall-pos))))
  ;; @c3kit/feature :content }
  )
