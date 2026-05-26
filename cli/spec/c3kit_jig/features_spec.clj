(ns c3kit-jig.features-spec
  (:require [speclj.core :refer [describe it should= should-throw]]
            [c3kit-jig.features :as f]
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
                            {:ssr true :csp true} {})))

  (it "db line-eq: selected backend → code kept, marker stripped"
    (should= "org.xerial/sqlite-jdbc {:mvn/version \"3.46.0.0\"}"
             (f/strip ";; @c3kit/db :sqlite = org.xerial/sqlite-jdbc {:mvn/version \"3.46.0.0\"}"
                      {} {:db :sqlite})))

  (it "db line-eq: non-selected backend → line dropped"
    (should= ""
             (f/strip ";; @c3kit/db :sqlite = org.xerial/sqlite-jdbc {:mvn/version \"3.46.0.0\"}"
                      {} {:db :postgres})))

  (it "db line-eq: multi-line, keeps only selected"
    (should= ":bucket sqlite-local"
             (f/strip (lines ";; @c3kit/db :datomic-pro = :bucket datomic-local"
                             ";; @c3kit/db :sqlite      = :bucket sqlite-local"
                             ";; @c3kit/db :postgres    = :bucket postgres-local"
                             ";; @c3kit/db :memory      = :bucket memory-local")
                      {} {:db :sqlite})))

  (it "db line-eq: inside an open feature block, not interpreted"
    (let [src (lines ";; @c3kit/feature :ssr {"
                     ";; @c3kit/db :sqlite = :bucket sqlite-local"
                     ";; @c3kit/feature :ssr }")]
      ;; ssr ON → block kept; the db line-eq inside is treated as a plain line
      ;; (block context wins, line-eq only fires when stack empty).
      (should= ";; @c3kit/db :sqlite = :bucket sqlite-local"
               (f/strip src {:ssr true} {:db :sqlite})))))
