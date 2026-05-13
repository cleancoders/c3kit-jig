(ns acme.markdownc-spec
  (:require [acme.markdownc :as sut]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [describe it should= should-be-nil should-not-be-nil]]))

(describe "markdownc/->hiccup"

  (it "converts a heading"
    (should= [:h1 "Hello"] (sut/->hiccup "# Hello")))

  (it "converts a paragraph"
    (should= [:p "foo"] (sut/->hiccup "foo")))

  (it "returns nil for blank"
    (should-be-nil (sut/->hiccup ""))))

(describe "markdownc component registry"

  (it "register-component! / resolve-components"
    (sut/register-component! :my-quote (fn [props] [:blockquote (:text props)]))
    (let [out (sut/resolve-components [:div [:my-quote {:text "Hi"}]])]
      (should= :div (first out))
      (let [resolved-node (second out)]
        (should-not-be-nil resolved-node)
        (should= true (fn? (first resolved-node)))
        (should= {:text "Hi"} (second resolved-node))))))
