(ns acme.ssr.prerender-spec
  (:require [acme.ssr.prerender :as sut]
            ;; @c3kit/feature :content = [acme.content.core :as content]
            [acme.spec-helper]
            [speclj.core :refer [before describe it should-contain should-have-invoked should-not-have-invoked stub with-stubs]]
            [speclj.stub :as stub]))

(describe "prerender orchestrator guards"

  (with-stubs)

  (it "skips when prerender.js missing"
    (with-redefs [sut/script-exists? (constantly false)
                  sut/run-node!      (stub :run-node)]
      (sut/prerender!)
      (should-not-have-invoked :run-node)))

  (it "skips when node not installed"
    (with-redefs [sut/script-exists?  (constantly true)
                  sut/node-installed? (constantly false)
                  sut/run-node!       (stub :run-node)]
      (sut/prerender!)
      (should-not-have-invoked :run-node)))

  (it "invokes node when both guards pass"
    (with-redefs [sut/script-exists?  (constantly true)
                  sut/node-installed? (constantly true)
                  sut/run-node!       (stub :run-node {:return 0})]
      (sut/prerender!)
      (should-have-invoked :run-node))))

;; @c3kit/feature :content {
(describe "build-payload"

  (before (content/load!))

  (it "contains config and content"
    (let [payload (sut/build-payload)]
      (should-contain :config payload)
      (should-contain :content payload)
      (should-contain :blog (:content payload))
      (should-contain "2026-05-12-hello-world" (get-in payload [:content :blog])))))
;; @c3kit/feature :content }
