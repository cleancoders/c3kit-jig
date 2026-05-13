(ns c3kit-create.rename-spec
  (:require [speclj.core :refer [describe context it should= should-throw should should-not]]
            [c3kit-create.rename :as r]))

(describe "c3kit-create.rename"

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
      (should= "my-app" (r/validate-name "my-app" {"acme" {:hyphen true}})))))
