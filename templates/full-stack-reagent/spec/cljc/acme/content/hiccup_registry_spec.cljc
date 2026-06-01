(ns acme.content.hiccup-registry-spec
  (:require [acme.content.hiccup-registry :as sut]
            [speclj.core #?(:clj :refer :cljs :refer-macros)
             [after-all before describe it should-not-be-nil should=]]))

(describe "acme.content.hiccup-registry"

  ;; Registry is process-wide; clear between tests so cases don't leak.
  (before (reset! sut/registry {}))
  (after-all (reset! sut/registry {}))

  (it "register-component! adds an entry keyed by tag"
    (let [f (fn [props] [:span (:text props)])]
      (sut/register-component! :my-tag f)
      (should= f (get @sut/registry :my-tag))))

  (it "resolve-components leaves nodes with unregistered tags untouched"
    (let [hiccup [:div [:p "untouched"] [:not-registered {:x 1} "still here"]]]
      (should= hiccup (sut/resolve-components hiccup))))

  (it "resolve-components swaps registered tag for component fn"
    (sut/register-component! :my-quote (fn [props] [:blockquote (:text props)]))
    (let [out (sut/resolve-components [:div [:my-quote {:text "Hi"}]])]
      (should= :div (first out))
      (let [resolved (second out)]
        (should-not-be-nil resolved)
        (should= true (fn? (first resolved)))
        (should= {:text "Hi"} (second resolved)))))

  (it "resolve-components walks nested hiccup"
    (sut/register-component! :inner (fn [props] [:em (:txt props)]))
    (let [out (sut/resolve-components
               [:article
                [:section [:inner {:txt "deep"}]]])]
      (should= :article (first out))
      (let [section (second out)
            inner   (second section)]
        (should= true (fn? (first inner)))
        (should= {:txt "deep"} (second inner)))))

  (it "resolve-components is idempotent on scalar / nil"
    (should= "plain" (sut/resolve-components "plain"))
    (should= nil (sut/resolve-components nil))))
