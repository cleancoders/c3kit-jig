(ns c3kit-jig.rename-spec
  (:require [speclj.core :refer [describe context it should= should-throw should should-not]]
            [c3kit-jig.rename :as r]))

(describe "c3kit-jig.rename"

          (it "variants computes all four variants for a kebab name"
              (should= {:hyphen     "my-cool-app"
                        :underscore "my_cool_app"
                        :pascal     "MyCoolApp"
                        :upper      "MY_COOL_APP"}
                       (r/variants "my-cool-app")))

          (it "replace-token rewrites all variants of a source token in a string"
              (should= "MyCoolApp / my_cool_app / my_cool_app / MY_COOL_APP_DEV"
                       (r/replace-token "Acme / acme / acme / ACME_DEV"
                                        "acme"
                                        {:hyphen true :underscore true :pascal true :upper-prefix true}
                                        (r/variants "my-cool-app"))))

          (it "replace-token handles uppercase source token (e.g. ACME_) for :upper-prefix"
              (should= "MY_COOL_APP_ENV / MY_COOL_APP_DEV_SECRET"
                       (r/replace-token "ACME_ENV / ACME_DEV_SECRET"
                                        "ACME_"
                                        {:upper-prefix true}
                                        (r/variants "my-cool-app"))))

          (it "replace-many applies tokens in declared order; longest first"
              (should= "MyCoolApp.foo my_cool_app.bar"
                       (r/replace-many "Acme.foo acme.bar"
                                       {"acme" {:hyphen true :underscore true :pascal true}}
                                       (r/variants "my-cool-app"))))

          (it "reserved? rejects clojure-ish reserved names"
              (should (r/reserved? "clojure"))
              (should (r/reserved? "java"))
              (should (r/reserved? "cljs")))

          (it "reserved? accepts user names"
              (should-not (r/reserved? "my-app")))

          (context "validate-name"
            (it "throws on names colliding with a template's source token"
                (should-throw (r/validate-name "acme" {"acme" {:hyphen true}}))
                (should-throw (r/validate-name "clojure" {})))

            (it "throws on names that fail regex"
                (should-throw (r/validate-name "1bad" {}))
                (should-throw (r/validate-name "" {})))

            (it "returns the name on success"
                (should= "my-app" (r/validate-name "my-app" {"acme" {:hyphen true}}))))

          (context "replace-content (context-aware)"
            (let [user (r/variants "my-app")
                  toks {"acme" {:hyphen true :underscore true :pascal true}}]

              (it "clj: code symbols hyphenate, string literals stay underscore"
                  (should= "(ns my-app.core (:require [my-app.foo]))\n\"public/cljs/my_app_dev.js\""
                           (r/replace-content
                            "(ns acme.core (:require [acme.foo]))\n\"public/cljs/acme_dev.js\""
                            toks user "clj")))

              (it "clj: munged JS inside a string stays underscore"
                  (should= "\"goog.require('my_app.main')\""
                           (r/replace-content "\"goog.require('acme.main')\"" toks user "clj")))

              (it "edn: namespace args hyphenate, bare prefix stays underscore"
                  (should= "{:main-opts [\"-m\" \"my-app.main\"] :ns-prefix \"my_app\"}"
                           (r/replace-content
                            "{:main-opts [\"-m\" \"acme.main\"] :ns-prefix \"acme\"}"
                            toks user "edn")))

              (it "other ext: single-variant underscore as before"
                  (should= ".my_app { color: red }"
                           (r/replace-content ".acme { color: red }" toks user "css"))))))
