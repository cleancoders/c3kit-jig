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
