(ns acme.content-page-spec
  (:require [acme.content-page :as sut]
            [acme.page :as page]
            [speclj.core :refer-macros [describe context it should should= before]]))

(describe "content-page"

  (before (reset! page/state {}))

  (it "install! sets the current type and permalink"
    (sut/install! "blog" "hello")
    (should= "blog"  (get-in @page/state [:content-page :type]))
    (should= "hello" (get-in @page/state [:content-page :permalink])))

  (it "seeded data wins over fetch"
    (swap! page/state assoc-in [:content :blog "hello"] {:meta {:title "T"} :body [:p "B"]})
    (sut/install! "blog" "hello")
    (should= {:meta {:title "T"} :body [:p "B"]}
             (sut/current-post)))

  (it "current-post returns nil when nothing is loaded"
    (sut/install! "blog" "missing")
    (should= nil (sut/current-post))))
