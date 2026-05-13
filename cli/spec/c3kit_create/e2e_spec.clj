(ns c3kit-create.e2e-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.main :as main]
            [babashka.fs :as fs]))

(defn- run-main! [argv]
  (let [exit-atom (atom nil)]
    (with-redefs [main/exit (fn [c]
                              (reset! exit-atom c)
                              (throw (ex-info "exit" {:code c})))]
      (try
        (binding [*err* (java.io.StringWriter.)
                  *out* (java.io.StringWriter.)]
          (apply main/-main argv))
        (catch clojure.lang.ExceptionInfo _ nil)))
    @exit-atom))

(defn- tfix [] (str (fs/path (System/getProperty "user.dir") "test-fixtures")))

(describe "e2e scaffold against tiny-fixture"
  (it "produces a working scaffold with defaults"
    (let [work (str (fs/create-temp-dir))]
      (try
        (let [code (run-main! ["my-app" "-t" "tiny-fixture"
                                    "--template-dir" (tfix)
                                    "--target-parent" work
                                    "--yes"])]
          (should= 0 code)
          (should     (fs/exists? (fs/path work "my-app" "src" "my_app" "core.clj")))
          (should     (fs/exists? (fs/path work "my-app" ".git")))
          (should-not (fs/exists? (fs/path work "my-app" "c3kit-template.edn")))
          (let [content (slurp (fs/file (fs/path work "my-app" "src" "my_app" "core.clj")))]
            (should-not (re-find #"acme" content))
            (should     (re-find #"has-ssr\?" content))))
        (finally (fs/delete-tree work)))))

  (it "honors --feature override (turns ssr off)"
    (let [work (str (fs/create-temp-dir))]
      (try
        (let [code (run-main! ["my-app" "-t" "tiny-fixture"
                                    "--template-dir" (tfix)
                                    "--target-parent" work
                                    "--feature" "ssr=false"
                                    "--yes"])]
          (should= 0 code)
          (let [content (slurp (fs/file (fs/path work "my-app" "src" "my_app" "core.clj")))]
            (should-not (re-find #"has-ssr\?" content))))
        (finally (fs/delete-tree work)))))

  (it "honors --db override (picks postgres over sqlite default)"
    (let [work (str (fs/create-temp-dir))]
      (try
        (let [code (run-main! ["my-app" "-t" "tiny-fixture"
                                    "--template-dir" (tfix)
                                    "--target-parent" work
                                    "--db" "postgres"
                                    "--yes"])]
          (should= 0 code)
          (let [content (slurp (fs/file (fs/path work "my-app" "src" "my_app" "core.clj")))]
            (should     (re-find #"db-impl :postgres" content))
            (should-not (re-find #"db-impl :sqlite" content))))
        (finally (fs/delete-tree work)))))

  (it "rejects --db value not in manifest :db.options"
    (let [work (str (fs/create-temp-dir))]
      (try
        (let [code (run-main! ["my-app" "-t" "tiny-fixture"
                                    "--template-dir" (tfix)
                                    "--target-parent" work
                                    "--db" "mysql"
                                    "--yes"])]
          (should= 4 code))
        (finally (fs/delete-tree work))))))
