(ns acme.compile-cljs-spec
  (:require [acme.compile-cljs :as sut]
            [speclj.core :refer [describe it should should-not should=]]))

(describe "prerender-ns?"

          (it "true for a defmethod prerender? :foo"
              (should (sut/prerender-ns? "(defmethod page/prerender? :foo [_] true)")))

          (it "false for :default"
              (should-not (sut/prerender-ns? "(defmethod page/prerender? :default [_] false)")))

          (it "false for unrelated content"
              (should-not (sut/prerender-ns? "(defmethod page/render :foo [_] [:div])"))))

(describe "extract-ns"

          (it "pulls the ns symbol"
              (should= "acme.home" (sut/extract-ns "(ns acme.home (:require ...))"))))
