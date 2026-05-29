(ns acme.page-spec
  (:require-macros [speclj.core :refer [after around before before-all context describe it should should-be-nil
                                        should-contain should-have-invoked should-not should-not-be-nil
                                        should-not-contain should-not-have-invoked should-not= should= stub with
                                        with-stubs]])
  (:require [acme.page :as sut]
            [c3kit.apron.log :as log]
            [speclj.core]))

(def transition-events (atom []))
(defmethod sut/entering! :page.spec/test-page [_] (swap! transition-events conj :entered))
(defmethod sut/reentering! :page.spec/test-page [_] (swap! transition-events conj :reentered))
(defmethod sut/exiting! :page.spec/test-page [_] (swap! transition-events conj :exited))

(describe "Page"

          (around [it] (log/capture-logs (it)))
          (before (sut/clear!))

          (it "install-page! - single"
              (sut/install-page! :first)
              (should= :first @sut/current))

          (it "install-page! - two"
              (sut/install-page! :first)
              (sut/install-page! :second)
              (should= :second @sut/current)
              (should= :first @sut/previous))

          (context "transition"

            (before (sut/clear!)
                    (reset! transition-events []))

            (it "entering"
                (sut/transition :page.spec/test-page)
                (should= [:entered] @transition-events))

            (it "exiting"
                (sut/install-page! :page.spec/test-page)
                (should-not (:exited? @transition-events))
                (sut/transition :some.other/page)
                (should= [:exited] @transition-events))

            (it "doesn't enter or exit when transitioning to same page"
                (sut/install-page! :page.spec/test-page)
                (sut/transition :page.spec/test-page)
                (should= [:reentered] @transition-events)))

          (describe "page/prerender?"

                    (it "returns false by default"
                        (should-not (sut/prerender? :anything)))

                    (it "returns true when a page opts in"
                        (defmethod sut/prerender? ::test-page [_] true)
                        (should (sut/prerender? ::test-page))
                        (remove-method sut/prerender? ::test-page))))
