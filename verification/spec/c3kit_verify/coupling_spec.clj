(ns c3kit-verify.coupling-spec
  (:require [speclj.core :refer [describe it should= should-be-nil]]
            [c3kit-verify.coupling :as sut]))

(def ^:private MANIFEST
  {:namespace-token "acme"
   :features [{:id :content :extras ["content/"]}
              {:id :ssr     :extras ["package.json"
                                     "resources/prerender/"
                                     "dev/acme/compile_cljs.clj"
                                     "spec/clj/acme/compile_cljs_spec.clj"]}
              {:id :markdownc}
              {:id :auth    :extras ["src/cljc/acme/schema/user.cljc"]}
              {:id :websocket}]})

(describe "file-owner"
  (it "ns-token/feature dir → that feature"
    (should= :content (sut/file-owner "src/clj/acme/content/core.clj" MANIFEST)))

  (it "ns-token/feature.clj file → that feature"
    (should= :auth (sut/file-owner "src/cljs/acme/auth.cljs" MANIFEST)))

  (it "ns-token/feature_spec.clj file → that feature"
    (should= :ssr (sut/file-owner "spec/clj/acme/ssr_spec.clj" MANIFEST)))

  (it "extras file → that feature"
    (should= :auth (sut/file-owner "src/cljc/acme/schema/user.cljc" MANIFEST)))

  (it "extras dir prefix → that feature"
    (should= :ssr (sut/file-owner "resources/prerender/foo.js" MANIFEST)))

  (it "extras explicit file → that feature"
    (should= :ssr (sut/file-owner "spec/clj/acme/compile_cljs_spec.clj" MANIFEST)))

  (it "no match → :always"
    (should= :always (sut/file-owner "src/clj/acme/main.clj" MANIFEST))))

(describe "extract-ns"
  (it "finds the ns symbol"
    (should= "acme.foo.bar"
             (sut/extract-ns "(ns acme.foo.bar\n  (:require [x]))")))
  (it "returns nil when no ns form"
    (should-be-nil (sut/extract-ns ";; just a comment"))))

(describe "extract-acme-refs"
  (it "captures vector requires"
    (should= ["acme.content.core" "acme.ssr.prerender"]
             (sut/extract-acme-refs "  [acme.content.core :as content] [acme.ssr.prerender :as p]" "acme")))
  (it "ignores requires for unrelated tokens"
    (should= [] (sut/extract-acme-refs "[clojure.string :as str]" "acme")))
  (it "captures fully-qualified symbol refs"
    (should= ["acme.content.core"]
             (sut/extract-acme-refs "(content/load!) (acme.content.core/types)" "acme"))))

(describe "line-eq-feature"
  (it "returns feature kw for ;; @c3kit/feature :X = line"
    (should= :content
             (sut/line-eq-feature "            ;; @c3kit/feature :content = [acme.content.core :as c]")))
  (it "nil for unrelated comment"
    (should-be-nil (sut/line-eq-feature ";; just a comment")))
  (it "nil for block opener"
    (should-be-nil (sut/line-eq-feature ";; @c3kit/feature :content {"))))

(describe "update-stack"
  (it "pushes on block open"
    (should= [{:kind :feature :id :content}]
             (sut/update-stack [] ";; @c3kit/feature :content {")))
  (it "pops on block close"
    (should= []
             (sut/update-stack [{:kind :feature :id :content}]
                               ";; @c3kit/feature :content }")))
  (it "ignores non-marker lines"
    (should= [{:kind :feature :id :auth}]
             (sut/update-stack [{:kind :feature :id :auth}]
                               "  [some.ns :as n]"))))

(describe "file-couplings"
  (let [ns->owner {"acme.content.core" :content
                   "acme.ssr.prerender" :ssr
                   "acme.auth.user"    :auth
                   "acme.main"         :always}]
    (it "flags an unguarded require of a feature-owned ns from an :always file"
      (let [content "(ns acme.main\n  (:require [acme.content.core :as c]))\n"
            v (sut/file-couplings {:relpath "src/clj/acme/main.clj" :owner :always
                                   :content content}
                                  ns->owner "acme")]
        (should= 1 (count v))
        (should= :content (:ref-owner (first v)))
        (should= "acme.content.core" (:ref (first v)))))

    (it "passes when require is gated by a matching line-eq marker"
      (let [content (str "(ns acme.main\n"
                         "  (:require ;; @c3kit/feature :content = [acme.content.core :as c]\n"
                         "            [clojure.string :as str]))\n")
            v (sut/file-couplings {:relpath "src/clj/acme/main.clj" :owner :always
                                   :content content}
                                  ns->owner "acme")]
        (should= 0 (count v))))

    (it "passes when reference is inside a matching block"
      (let [content (str "(ns acme.main)\n"
                         ";; @c3kit/feature :content {\n"
                         "(defn x [] (acme.content.core/types))\n"
                         ";; @c3kit/feature :content }\n")
            v (sut/file-couplings {:relpath "src/clj/acme/main.clj" :owner :always
                                   :content content}
                                  ns->owner "acme")]
        (should= 0 (count v))))

    (it "passes when owner of current file matches ref-owner"
      (let [content "(ns acme.content.page)\n(acme.content.core/types)\n"
            v (sut/file-couplings {:relpath "src/clj/acme/content/page.clj" :owner :content
                                   :content content}
                                  ns->owner "acme")]
        (should= 0 (count v))))

    (it "flags unguarded ref to a different feature even from a feature-owned file"
      (let [content "(ns acme.ssr.prerender\n  (:require [acme.content.core :as c]))\n"
            v (sut/file-couplings {:relpath "src/clj/acme/ssr/prerender.clj" :owner :ssr
                                   :content content}
                                  ns->owner "acme")]
        (should= 1 (count v))
        (should= :content (:ref-owner (first v)))))))
