(ns acme.routes-spec
  (:require [acme.content]
            [acme.routes :as routes]
            [acme.spec-helper]
            [acme.test-data :as test-data]
            [acme.user.api]
            [acme.user.web]
            [c3kit.wire.spec-helper :as wire-helper]
            [c3kit.wire.websocket :as ws]
            [speclj.core :refer :all]
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

(require '[acme.sandbox.core])

(describe "Routes"
  (with-stubs)
  (test-data/with-memory-kinds :user)
  (before (reset! args :none)
          (acme.content/load!))
  (around [it] (with-redefs [c3kit.wire.api/version (constantly "fake-api-version")] (it)))

  ; Please keep these specs sorted alphabetically

  ;; web routes
  (it "GET / serves prerendered home"
    (with-redefs [acme.layouts/prerendered-html (constantly "<h1>FROM PRERENDER</h1>")]
      (let [response (routes/handler {:uri "/" :request-method :get})]
        (should= 200 (:status response))
        (should (re-find #"FROM PRERENDER" (:body response))))))
  (test-route "/apple/oauth" :post acme.user.web/web-apple-oauth-login)
  (test-route "/forgot-password" :get acme.layouts/web-rich-client)
  (test-route "/google/oauth" :post acme.user.web/web-google-oauth-login)
  (test-route "/recover-password/foo" :get acme.layouts/web-rich-client)
  (test-route "/sandbox/example-page" :get acme.sandbox.core/handler)
  (test-route "/signout" :any acme.user.web/web-signout)
  (test-route "/user/websocket" :any acme.user.web/websocket-open)

  ;; ajax routes
  (test-route "/ajax/forgot-password" :post acme.user.ajax/ajax-forgot-password)
  (test-route "/ajax/recover-password" :post acme.user.ajax/ajax-reset-password)
  (test-route "/ajax/spinner" :get acme.routes/spinner)
  (test-route "/ajax/user/csrf-token" :get acme.user.ajax/ajax-csrf-token)
  (test-route "/ajax/user/signin" :post acme.user.ajax/ajax-signin)
  (test-route "/ajax/user/signup" :post acme.user.ajax/ajax-signup)

  ;; api routes
  (test-route "/api/v1/content/blog/2026-05-12-hello-world" :get acme.content/api-fetch-post)
  (test-route "/api/user/forgot-password" :post acme.user.api/api-forgot-password)
  (test-route "/api/user/reset-password/some-token" :post acme.user.api/api-reset-password)
  (test-route "/api/user/signin" :post acme.user.api/api-signin)
  (test-route "/api/user/signup" :post acme.user.api/api-signup)
  (test-route "/api/user/social/google" :post acme.user.api/api-social-auth)
  (test-route "/api/user/social/apple" :post acme.user.api/api-social-auth)

  ;; websocket handlers
  (test-webs :user/fetch-data acme.user.web/ws-fetch-user-data)

  ;; content auto-routes
  (it "GET /blog reaches content/web-list"
    (let [response (routes/handler {:uri "/blog" :request-method :get})]
      (should= 200 (:status response))
      (should (re-find #"Hello, world" (:body response)))))

  (it "GET /blog/2026-05-12-hello-world reaches content/web-post"
    (let [response (routes/handler {:uri "/blog/2026-05-12-hello-world" :request-method :get :params {:permalink "2026-05-12-hello-world"}})]
      (should= 200 (:status response))
      (should (re-find #"Hello, world" (:body response)))))

  (it "not-found global - nil - handled by http"
    (let [response (routes/handler {:uri "/blah" :request-method :get})]
      (should-be-nil response)))

  (it "spinner"
    (with-redefs [routes/sleep-for-10 (stub :sleep)]
      (wire-helper/should-be-ajax-ok (routes/spinner :blah) nil)
      (should-have-invoked :sleep)))

  (it "content catch-all is declared after explicit frontend routes"
    (let [src (slurp "src/cljs/acme/routes.cljs")
          explicit-pos (.indexOf src "/recover-password")
          catchall-pos (.indexOf src "/:content-type")]
      (should (pos? catchall-pos))
      (should (< explicit-pos catchall-pos))))

  )
