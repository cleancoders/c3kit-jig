(ns c3kit-create.e2e-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.main :as main]
            [babashka.fs :as fs]))

(defn- run-main!
  ([argv] (run-main! argv ""))
  ([argv stdin-text]
   (let [exit-atom (atom nil)]
     (with-redefs [main/exit (fn [c]
                               (reset! exit-atom c)
                               (throw (ex-info "exit" {:code c})))]
       (try
         (binding [*err* (java.io.StringWriter.)
                   *out* (java.io.StringWriter.)
                   *in*  (java.io.BufferedReader. (java.io.StringReader. stdin-text))]
           (apply main/-main argv))
         (catch clojure.lang.ExceptionInfo _ nil)))
     @exit-atom)))

(defn- tfix [] (str (fs/path (System/getProperty "user.dir") "test-fixtures")))

(describe "e2e scaffold against tiny-fixture"
  (it "--yes without --template exits 2 (non-interactive demands explicit)"
    (should= 2 (run-main! ["--yes"]))
    (should= 2 (run-main! ["my-app" "--yes"])))

  (it "prompts for missing --name and lets user pick template from list"
    (let [work (str (fs/create-temp-dir))]
      (try
        ;; stdin: name="my-app", then choose template #1 from the listed menu
        (let [code (run-main! ["--template-dir" (tfix)
                                "--target-parent" work
                                "--no-git"]
                              "my-app\n1\n")]
          (should= 0 code)
          (should (fs/exists? (fs/path work "my-app" "src" "my_app" "core.clj"))))
        (finally (fs/delete-tree work)))))

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
        (finally (fs/delete-tree work)))))

  (it ":extras removes file for ssr=false"
    (let [work (str (fs/create-temp-dir))]
      (try
        (let [code (run-main! ["my-app" "-t" "tiny-fixture"
                                    "--template-dir" (tfix)
                                    "--target-parent" work
                                    "--feature" "ssr=false"
                                    "--yes"])]
          (should= 0 code)
          ;; tiny-fixture's :ssr :extras → resources/Acme.css
          ;; → after path rename → resources/MyApp.css
          (should-not (fs/exists? (fs/path work "my-app" "resources" "MyApp.css"))))
        (finally (fs/delete-tree work)))))

  (it "interactively prompts for features and db when --yes not passed"
    (let [work (str (fs/create-temp-dir))]
      (try
        ;; stdin: ssr=n, legacy=n, db pick #2 (postgres)
        (let [code (run-main! ["my-app" "-t" "tiny-fixture"
                                    "--template-dir" (tfix)
                                    "--target-parent" work
                                    "--no-git"]
                              "n\nn\n2\n")]
          (should= 0 code)
          (let [content (slurp (fs/file (fs/path work "my-app" "src" "my_app" "core.clj")))]
            (should-not (re-find #"has-ssr\?" content))
            (should     (re-find #"db-impl :postgres" content)))
          (should-not (fs/exists? (fs/path work "my-app" "src" "my_app_legacy"))))
        (finally (fs/delete-tree work)))))

  (it "interactive prompts skip features overridden via CLI --feature"
    (let [work (str (fs/create-temp-dir))]
      (try
        ;; ssr fixed off via CLI. stdin: legacy=y, db pick #1 (sqlite default)
        (let [code (run-main! ["my-app" "-t" "tiny-fixture"
                                    "--template-dir" (tfix)
                                    "--target-parent" work
                                    "--no-git"
                                    "--feature" "ssr=false"]
                              "y\n1\n")]
          (should= 0 code)
          (let [content (slurp (fs/file (fs/path work "my-app" "src" "my_app" "core.clj")))]
            (should-not (re-find #"has-ssr\?" content))
            (should     (re-find #"db-impl :sqlite" content)))
          (should (fs/exists? (fs/path work "my-app" "src" "my_app_legacy"))))
        (finally (fs/delete-tree work)))))

  (it ":extras removes directory for legacy=false"
    (let [work (str (fs/create-temp-dir))]
      (try
        (let [code (run-main! ["my-app" "-t" "tiny-fixture"
                                    "--template-dir" (tfix)
                                    "--target-parent" work
                                    "--feature" "legacy=false"
                                    "--yes"])]
          (should= 0 code)
          ;; tiny-fixture's :legacy :extras → src/acme_legacy/
          ;; → after path rename → src/my_app_legacy/
          (should-not (fs/exists? (fs/path work "my-app" "src" "my_app_legacy"))))
        (finally (fs/delete-tree work))))))
