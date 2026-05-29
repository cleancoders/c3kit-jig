(ns c3kit-verify.checks-spec
  (:require [speclj.core :refer [describe context it should= should should-not should-be-nil]]
            [c3kit-verify.checks :as sut]))

(describe "ns-prefix-violation"
          (it "flags an underscore ns prefix"
              (should= "my_app.main"
                       (sut/ns-prefix-violation "(ns my_app.main\n  (:require [x]))" "my_app")))
          (it "flags an underscore ns prefix on a nested ns"
              (should= "my_app.auth.user"
                       (sut/ns-prefix-violation "(ns my_app.auth.user)" "my_app")))
          (it "passes a hyphenated ns prefix"
              (should-be-nil (sut/ns-prefix-violation "(ns my-app.main)" "my_app")))
          (it "ignores underscores that are not the ns prefix (env strings in body)"
              (should-be-nil (sut/ns-prefix-violation "(ns my-app.config)\n(def e \"my_app.env\")" "my_app")))
          (it "returns nil when there is no ns form"
              (should-be-nil (sut/ns-prefix-violation ";; just a comment\n(+ 1 2)" "my_app"))))

(describe "clean-spec-output?"
          (it "passes plain speclj output"
              (should (:ok? (sut/clean-spec-output? "....\n\n4 examples, 0 failures\n"))))
          (it "fails on a WARN line"
              (let [r (sut/clean-spec-output? "....\n2024-01-01 12:00:00 WARN something happened\n3 examples, 0 failures")]
                (should-not (:ok? r))
                (should= 1 (count (:offending r)))))
          (it "fails on a bare level marker"
              (should-not (:ok? (sut/clean-spec-output? "INFO booting\n1 examples, 0 failures"))))
          (it "fails on an ISO timestamp line with no level"
              (should-not (:ok? (sut/clean-spec-output? "2026-05-27T10:00:00 starting\n1 examples, 0 failures")))))

(describe "tool-result"
          (it "fails when the config file is absent"
              (let [r (sut/tool-result {:config-exists? false :exit 0 :tool "clj-kondo" :config ".clj-kondo/config.edn"})]
                (should-not (:ok? r))
                (should (re-find #"no .clj-kondo/config.edn" (:detail r)))))
          (it "passes on config present + exit 0"
              (should (:ok? (sut/tool-result {:config-exists? true :exit 0 :tool "cljfmt" :config "cljfmt.edn"}))))
          (it "fails on config present + nonzero exit"
              (should-not (:ok? (sut/tool-result {:config-exists? true :exit 2 :tool "clj-kondo" :config ".clj-kondo/config.edn"})))))

(describe "parse-cljs-result"
          (it "passes on examples>0 and 0 failures and exit 0"
              (should (:ok? (sut/parse-cljs-result "12 examples, 0 failures" 0))))
          (it "fails on failures>0"
              (should-not (:ok? (sut/parse-cljs-result "12 examples, 3 failures" 0))))
          (it "fails when no examples ran"
              (should-not (:ok? (sut/parse-cljs-result "0 examples, 0 failures" 0))))
          (it "fails on nonzero exit"
              (should-not (:ok? (sut/parse-cljs-result "5 examples, 0 failures" 1)))))

(require '[babashka.fs :as fs])

(defn- spit-file! [root rel content]
  (let [f (fs/file (fs/path root rel))]
    (fs/create-dirs (fs/parent f))
    (spit f content)))

(defn- temp-scaffold! []
  (let [root (str (fs/create-temp-dir {:prefix "harness-test-"}))]
    (spit-file! root "src/clj/my_app/main.clj" "(ns my_app.main)\n")
    (spit-file! root "src/clj/my_app/config.clj" "(ns my-app.config)\n(def e \"my_app.env\")\n")
    (spit-file! root "deps.edn" "{:paths [\"src\"]}\n")
    root))

(describe "cruft-check"
          (it "flags an .iml and a .cpcache dir"
              (let [root (temp-scaffold!)]
                (spit-file! root "full-stack-reagent.iml" "x")
                (spit-file! root ".cpcache/foo.edn" "x")
                (let [r (sut/cruft-check root ["*.iml" ".cpcache"])]
                  (should-not (:ok? r))
                  (should (re-find #"full-stack-reagent.iml" (:detail r)))
                  (fs/delete-tree root))))
          (it "passes a clean scaffold"
              (let [root (temp-scaffold!)
                    r    (sut/cruft-check root ["*.iml" ".cpcache" "target"])]
                (should (:ok? r))
                (fs/delete-tree root)))
          (it "detects a hidden .cpcache dir via literal denylist entry"
              (let [root (temp-scaffold!)]
                (spit-file! root ".cpcache/foo.edn" "x")
                (let [r (sut/cruft-check root [".cpcache"])]
                  (should-not (:ok? r))
                  (should (re-find #"\.cpcache" (:detail r)))
                  (fs/delete-tree root))))
          (it "detects a literal nested dir (spec/combos)"
              (let [root (temp-scaffold!)]
                (spit-file! root "spec/combos/x.edn" "x")
                (let [r (sut/cruft-check root ["spec/combos"])]
                  (should-not (:ok? r))
                  (fs/delete-tree root)))))

(describe "ns-hyphen-check"
          (it "flags the underscore ns form, ignores body strings and hyphenated ns"
              (let [root (temp-scaffold!)
                    r    (sut/ns-hyphen-check root "my_app" [])]
                (should-not (:ok? r))
                (should (re-find #"my_app.main" (:detail r)))
                (should-not (re-find #"config.clj" (:detail r)))
                (fs/delete-tree root))))

(describe "residue-check"
          (it "flags a surviving @c3kit/feature marker"
              (let [root (temp-scaffold!)]
                (spit-file! root "src/clj/my_app/x.clj" ";; @c3kit/feature :auth = foo")
                (let [r (sut/residue-check root)]
                  (should-not (:ok? r))
                  (fs/delete-tree root))))
          (it "passes with no markers"
              (let [root (temp-scaffold!)
                    r    (sut/residue-check root)]
                (should (:ok? r))
                (fs/delete-tree root)))
          (it "fails on a grep error (nonexistent root)"
              (let [r (sut/residue-check "/nonexistent-path-xyz-12345")]
                (should-not (:ok? r)))))

(describe "combo-check"
          (it "checks must-exist / must-not-exist / file-contains / file-not-contains"
              (let [root (temp-scaffold!)
                    ok   (sut/combo-check root {:must-exist ["deps.edn"]
                                                :must-not-exist ["nope.txt"]
                                                :file-contains {"deps.edn" [":paths"]}
                                                :file-not-contains {"deps.edn" ["banana"]}})
                    bad  (sut/combo-check root {:must-exist ["missing.clj"]})]
                (should (:ok? ok))
                (should-not (:ok? bad))
                (fs/delete-tree root))))

(describe "clj-clean-check (via injected runner)"
          (it "passes clean output"
              (let [r (sut/clj-clean-check* {:exit 0 :out "....\n4 examples, 0 failures"})]
                (should (:ok? r))))
          (it "fails on log noise even with exit 0"
              (let [r (sut/clj-clean-check* {:exit 0 :out "WARN boom\n4 examples, 0 failures"})]
                (should-not (:ok? r))))
          (it "fails on nonzero exit"
              (let [r (sut/clj-clean-check* {:exit 1 :out "4 examples, 1 failures"})]
                (should-not (:ok? r)))))

(describe "tool-check (via injected result)"
          (it "fails when config missing"
              (should-not (:ok? (sut/tool-check* :lint {:config-exists? false :exit 0
                                                        :tool "clj-kondo" :config ".clj-kondo/config.edn"}))))
          (it "tags the check keyword"
              (should= :fmt (:check (sut/tool-check* :fmt {:config-exists? true :exit 0
                                                           :tool "cljfmt" :config "cljfmt.edn"})))))

(describe "cljs-check (via injected runner)"
          (it "passes on a green one-shot run"
              (should (:ok? (sut/cljs-check* {:exit 0 :out "12 examples, 0 failures"}))))
          (it "fails when it never ran"
              (should-not (:ok? (sut/cljs-check* {:exit 0 :out "0 examples, 0 failures"}))))
          (it "fails on log noise even with green summary"
              (let [out "2026-05-29T15:32:27.738Z DEBUG [my-app.routes] - dispatching:  /\n12 examples, 0 failures"
                    r   (sut/cljs-check* {:exit 0 :out out})]
                (should-not (:ok? r))
                (should (re-find #"log noise" (:detail r)))))
          (it "fails on a WARN line even with green summary"
              (let [r (sut/cljs-check* {:exit 0 :out "WARN [c3kit.apron.schema] - deprecated\n5 examples, 0 failures"})]
                (should-not (:ok? r))))
          (it "fails on nonzero exit"
              (should-not (:ok? (sut/cljs-check* {:exit 1 :out "5 examples, 0 failures"})))))
