(ns c3kit-jig.format-spec
  (:require [babashka.fs :as fs]
            [c3kit-jig.format :as format]
            [speclj.core :refer [describe it should= should-not]]))

(defn- mk-stage []
  (let [d (fs/create-temp-dir {:prefix "fmt-spec-"})]
    (doseq [sub ["src" "spec" "dev"]] (fs/create-dirs (fs/path d sub)))
    d))

(describe "c3kit-jig.format"

          (it "format!: reformats clj/cljc/cljs under src/spec/dev using shipped cljfmt.edn"
              (let [stage (mk-stage)
                    src   (fs/file (fs/path stage "src" "demo.clj"))]
                (spit (fs/file (fs/path stage "cljfmt.edn")) "{}")
                (spit src "(defn x [a   b]\n(+ a   b)\n)\n")
                (format/format! stage)
                (should= "(defn x [a   b]\n  (+ a   b))\n" (slurp src))
                (fs/delete-tree (str stage))))

          (it "format!: no-op when cljfmt.edn is absent"
              (let [stage (mk-stage)
                    src   (fs/file (fs/path stage "src" "demo.clj"))
                    orig  "(defn x [a   b]\n(+ a   b)\n)\n"]
                (spit src orig)
                (format/format! stage)
                (should= orig (slurp src))
                (fs/delete-tree (str stage))))

          (it "format!: skips files outside src/spec/dev and non-clj extensions"
              (let [stage (mk-stage)
                    edn   (fs/file (fs/path stage "src" "data.edn"))
                    off   (fs/file (fs/path stage "extra.clj"))
                    orig  "(  +    1   2)\n"]
                (spit (fs/file (fs/path stage "cljfmt.edn")) "{}")
                (spit edn orig)
                (spit off orig)
                (format/format! stage)
                (should= orig (slurp edn))
                (should= orig (slurp off))
                (fs/delete-tree (str stage)))))
