(ns c3kit-create.features-spec
  (:require [speclj.core :refer [describe it should= should-throw]]
            [c3kit-create.features :as f]
            [clojure.string :as str]))

(defn lines [& xs] (str/join "\n" xs))

(describe "features/strip"
  (it "removes block content when feature is OFF"
    (should= (lines "before" "after")
             (f/strip (lines "before"
                             ";; @c3kit/feature :ssr {"
                             "(prerender!)"
                             ";; @c3kit/feature :ssr }"
                             "after")
                      {:ssr false} {})))

  (it "keeps block content but drops marker lines when feature is ON"
    (should= (lines "before" "(prerender!)" "after")
             (f/strip (lines "before"
                             ";; @c3kit/feature :ssr {"
                             "(prerender!)"
                             ";; @c3kit/feature :ssr }"
                             "after")
                      {:ssr true} {})))

  (it "supports inverse markers"
    (should= (lines "alt")
             (f/strip (lines ";; @c3kit/feature !:auth {"
                             "alt"
                             ";; @c3kit/feature !:auth }")
                      {:auth false} {}))
    (should= ""
             (f/strip (lines ";; @c3kit/feature !:auth {"
                             "alt"
                             ";; @c3kit/feature !:auth }")
                      {:auth true} {})))

  (it "line-level toggle on"
    (should= "(require '[csp :refer [wrap]])"
             (f/strip ";; @c3kit/feature :csp = (require '[csp :refer [wrap]])"
                      {:csp true} {})))

  (it "line-level toggle off removes the line"
    (should= ""
             (f/strip ";; @c3kit/feature :csp = (require '[csp :refer [wrap]])"
                      {:csp false} {})))

  (it "db markers keep only matching block"
    (should= "{:impl :sqlite}"
             (f/strip (lines ";; @c3kit/db :sqlite {"
                             "{:impl :sqlite}"
                             ";; @c3kit/db :sqlite }"
                             ";; @c3kit/db :postgres {"
                             "{:impl :postgres}"
                             ";; @c3kit/db :postgres }")
                      {} {:db :sqlite})))

  (it "errors on unclosed block"
    (should-throw (f/strip (lines ";; @c3kit/feature :ssr {"
                                   "(prerender!)")
                            {:ssr true} {})))

  (it "errors on mismatched id"
    (should-throw (f/strip (lines ";; @c3kit/feature :ssr {"
                                   "x"
                                   ";; @c3kit/feature :csp }")
                            {:ssr true :csp true} {})))

  (it "errors on nested markers"
    (should-throw (f/strip (lines ";; @c3kit/feature :ssr {"
                                   ";; @c3kit/feature :csp {"
                                   "x"
                                   ";; @c3kit/feature :csp }"
                                   ";; @c3kit/feature :ssr }")
                            {:ssr true :csp true} {}))))
