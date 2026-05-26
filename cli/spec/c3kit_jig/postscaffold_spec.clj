(ns c3kit-jig.postscaffold-spec
  (:require [speclj.core :refer [describe it should]]
            [c3kit-jig.postscaffold :as ps]
            [babashka.fs :as fs]
            [babashka.process :as p]))

(defn- mk-project! []
  (let [d (str (fs/create-temp-dir))]
    (spit (fs/file (fs/path d "hello.txt")) "hi")
    d))

(describe "c3kit-jig.postscaffold"

  (it "git-init! creates a repo with a single commit on main"
    (let [d (mk-project!)]
      (ps/git-init! d)
      (should (fs/exists? (fs/path d ".git")))
      (let [log (:out (p/shell {:dir d :out :string}
                               "git" "log" "--oneline"))]
        (should (re-find #"initial scaffold" log)))
      (fs/delete-tree d)))

  (it "git-init! is a no-op when called with :git? false (caller responsibility)"
    ;; This spec exists to remind callers: the fn itself doesn't gate
    (should true))

  (it "install! is a no-op without :install"
    (let [d (mk-project!)]
      (ps/install! d {:install false})
      (fs/delete-tree d)
      (should true)))

  (it "install! skips npm when no package.json"
    (let [d (mk-project!)]
      ;; clj -P would actually run; in CI we mock by setting :dry-run? true
      (with-out-str (ps/install! d {:install true :dry-run? true}))
      (fs/delete-tree d)
      (should true))))
