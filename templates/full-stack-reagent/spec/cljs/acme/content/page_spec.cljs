(ns acme.content.page-spec
  (:require [acme.content.hiccup-registry :as registry]
            [acme.content.page :as sut]
            [acme.page :as page]
            [c3kit.wire.ajax :as ajax]
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
    (should= nil (sut/current-post)))

  (it "fetch! GETs the ajax content endpoint for the given type and permalink"
    (let [captured (atom nil)]
      (with-redefs [ajax/get! (fn [url & _] (reset! captured url))]
        (sut/fetch! "blog" "hello" nil)
        (should= "/ajax/content/blog/hello" @captured))))

  (it "fetch-list! GETs the ajax content list endpoint for the given type"
    (let [captured (atom nil)]
      (with-redefs [ajax/get! (fn [url & _] (reset! captured url))]
        (sut/fetch-list! "blog" nil)
        (should= "/ajax/content/blog" @captured))))

  (it "current-list returns stored summaries for the current type"
    (sut/install! "blog" nil)
    (swap! page/state assoc-in [:content :blog ::sut/list]
           [{:permalink "p1" :meta {:title "First"}}])
    (should= [{:permalink "p1" :meta {:title "First"}}] (sut/current-list)))

  (it "content-view renders the post list (not Loading) when the list is loaded and no permalink"
    (sut/install! "blog" nil)
    (swap! page/state assoc-in [:content :blog ::sut/list]
           [{:permalink "p1" :meta {:title "First Post"}}])
    (let [flat (tree-seq coll? seq (sut/content-view))]
      (should (some #{"First Post"} flat))
      (should-not (some #{"Loading..."} flat))))

  (it "reentering! fetches the post on soft-nav within :content/page"
    ;; secretary routes /blog and /blog/x to the same page key, so the
    ;; transition is a reenter, not an enter. It must still fetch.
    (let [captured (atom nil)]
      (with-redefs [ajax/get! (fn [url & _] (reset! captured url))]
        (sut/install! "blog" "hello")
        (page/reentering! :content/page)
        (should= "/ajax/content/blog/hello" @captured))))

  (it "reentering! fetches the list on soft-nav to a list route"
    (let [captured (atom nil)]
      (with-redefs [ajax/get! (fn [url & _] (reset! captured url))]
        (sut/install! "blog" nil)
        (page/reentering! :content/page)
        (should= "/ajax/content/blog" @captured)))))

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

(describe "install-components!"

  (before (reset! registry/registry {}))
  (after  (reset! registry/registry {}))

  (it "registers every entry in the components map"
    (should= {} @registry/registry)
    (sut/install-components!)
    (doseq [[k f] sut/components]
      (should= f (get @registry/registry k))))

  (it "no top-level side effect — registry stays empty until install! runs"
    ;; Loading the namespace must not mutate the registry. This guards
    ;; against accidentally re-introducing a top-level register call.
    (should= {} @registry/registry)))
