(ns c3kit-create.e2e-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.main :as main]
            [babashka.fs :as fs]))

(describe "e2e scaffold against tiny-fixture"
  (it "produces a working scaffold with defaults"
    (let [work (str (fs/create-temp-dir))
          cwd  (System/getProperty "user.dir")
          tfix (str (fs/path cwd "test-fixtures"))
          exit-atom (atom nil)]
      (try
        (with-redefs [main/exit (fn [c]
                                  (reset! exit-atom c)
                                  (throw (ex-info "exit" {:code c})))]
          (try
            (binding [*err* (java.io.StringWriter.)
                      *out* (java.io.StringWriter.)]
              (main/-main "my-app" "-t" "tiny-fixture"
                          "--template-dir" tfix
                          "--target-parent" work
                          "--yes"))
            (catch clojure.lang.ExceptionInfo _ nil)))
        (should= 0 @exit-atom)
        (should     (fs/exists? (fs/path work "my-app" "src" "my_app" "core.clj")))
        (should     (fs/exists? (fs/path work "my-app" ".git")))
        (should-not (fs/exists? (fs/path work "my-app" "c3kit-template.edn")))
        (let [content (slurp (fs/file (fs/path work "my-app" "src" "my_app" "core.clj")))]
          (should-not (re-find #"acme" content))
          (should     (re-find #"has-ssr\?" content)))
        (finally
          (fs/delete-tree work))))))
