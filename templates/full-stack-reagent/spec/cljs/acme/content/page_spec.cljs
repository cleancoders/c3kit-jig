(ns acme.content.page-spec
  (:require [acme.content.hiccup-registry :as registry]
            [acme.content.page :as sut]
            [acme.page :as page]
            [speclj.core :refer-macros [after before describe it should should-not should=]]))

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

(describe "render-post :default — registry resolution"

  (before (reset! registry/registry {}))
  (after  (reset! registry/registry {}))

  (it "swaps a registered keyword tag in :body for the component fn"
    (let [quote-fn (fn [props] [:blockquote (:text props)])
          post     {:meta {:title "T"}
                    :body [:div [:p "lead"] [:quote-block {:text "hi"}]]}]
      (registry/register-component! :quote-block quote-fn)
      (let [out      (sut/render-post :default post)
            section  (last out)
            body     (last section)
            resolved (last body)]
        ;; The registry must be applied to (:body post) before render.
        (should= :section.body (first section))
        (should= quote-fn (first resolved))
        (should= {:text "hi"} (second resolved))
        (should-not (some #{:quote-block} (tree-seq coll? seq out))))))

  (it "leaves unregistered keyword tags untouched"
    (let [post {:meta {} :body [:div [:not-registered {:x 1}]]}]
      (should (some #{:not-registered}
                    (tree-seq coll? seq (sut/render-post :default post)))))))
