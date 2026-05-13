(ns c3kit-create.render-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.render :as r]
            [babashka.fs :as fs]
            [clojure.edn :as edn]))

(defn- mk-template! [root]
  (let [d root]
    (fs/create-dirs (fs/path d "src" "acme"))
    (spit (fs/file (fs/path d "src" "acme" "core.clj"))
          "(ns acme.core)\n;; @c3kit/feature :ssr {\n(println \"ssr\")\n;; @c3kit/feature :ssr }\n")
    (spit (fs/file (fs/path d "Acme.css")) ".acme { color: red; }")
    (spit (fs/file (fs/path d "env"))     "KEY=ACME_DEV_SECRET")
    (spit (fs/file (fs/path d "c3kit-template.edn"))
          (pr-str {:id :tiny :name "Tiny" :description "x" :version "0.1.0" :min-cli "0.1.0"
                   :tokens {"acme" {:hyphen true :underscore true :pascal true}
                            "ACME_" {:upper-prefix true}}
                   :secrets [{:placeholder "ACME_DEV_SECRET" :bytes 4}]
                   :features [{:id :ssr :prompt "" :default true}]
                   :next-steps [{:cmd "cd {{name}}"}]}))))

(describe "render/render!"
  (it "applies tokens, markers, secrets, and path renames"
    (let [stage (str (fs/create-temp-dir))]
      (mk-template! stage)
      (r/render! stage
                 (edn/read-string (slurp (fs/file (fs/path stage "c3kit-template.edn"))))
                 "my-cool-app"
                 {:ssr false}        ; features map
                 {}                  ; db choice (no db markers)
                 "0.1.0-SNAPSHOT")
      ;; renames
      (should (fs/exists? (fs/path stage "src" "my_cool_app" "core.clj")))
      (should-not (fs/exists? (fs/path stage "src" "acme" "core.clj")))
      (should (fs/exists? (fs/path stage "MyCoolApp.css")))
      ;; content rewrites
      (let [core (slurp (fs/file (fs/path stage "src" "my_cool_app" "core.clj")))]
        (should= "(ns my_cool_app.core)" core))
      (let [css (slurp (fs/file (fs/path stage "MyCoolApp.css")))]
        (should (re-find #"\.my_cool_app" css)))
      ;; secrets
      (let [env (slurp (fs/file (fs/path stage "env")))]
        (should (re-find #"KEY=[0-9a-f]{8}" env)))
      ;; manifest removed
      (should-not (fs/exists? (fs/path stage "c3kit-template.edn")))
      (fs/delete-tree stage))))

(describe "render/render! README handling"
  (it "renames README.scaffold.md → README.md, replacing any existing README.md"
    (let [stage (str (fs/create-temp-dir))]
      (mk-template! stage)
      (spit (fs/file (fs/path stage "README.md"))          "TEMPLATE-DEV-README")
      (spit (fs/file (fs/path stage "README.scaffold.md")) "SCAFFOLDED-README")
      (r/render! stage
                 (edn/read-string (slurp (fs/file (fs/path stage "c3kit-template.edn"))))
                 "x" {:ssr false} {} "0.1.0-SNAPSHOT")
      (should= "SCAFFOLDED-README"
               (slurp (fs/file (fs/path stage "README.md"))))
      (should-not (fs/exists? (fs/path stage "README.scaffold.md")))
      (fs/delete-tree stage))))

(describe "render/render! hook"
  (it "invokes c3kit-template.bb when :hook? true, then deletes hook + context"
    (let [stage    (str (fs/create-temp-dir))
          marker   (fs/path stage "hook-ran.txt")
          manifest {:id      :hooked  :name "Hooked"  :description "x"
                    :version "0.1.0"  :min-cli "0.1.0"
                    :hook?   true
                    :tokens  {}       :secrets []     :features []
                    :next-steps [{:cmd "x"}]}]
      ;; hook script copies context.edn into a marker file to prove it saw it
      (spit (fs/file (fs/path stage "c3kit-template.bb"))
            "(require '[babashka.fs :as fs])
             (let [dir (first *command-line-args*)
                   ctx (slurp (str dir \"/.c3kit-create-context.edn\"))]
               (spit (str dir \"/hook-ran.txt\") ctx))")
      (spit (fs/file (fs/path stage "c3kit-template.edn")) (pr-str manifest))
      (r/render! stage manifest "x" {} {} "0.1.0-SNAPSHOT")
      (should     (fs/exists? marker))
      (let [ctx (read-string (slurp (fs/file marker)))]
        (should= "x" (:name ctx))
        (should= "0.1.0-SNAPSHOT" (:cli-version ctx))
        (should= :hooked (:template ctx)))
      (should-not (fs/exists? (fs/path stage "c3kit-template.bb")))
      (should-not (fs/exists? (fs/path stage ".c3kit-create-context.edn")))
      (fs/delete-tree stage)))

  (it "aborts with ex-info when hook exits non-zero"
    (let [stage    (str (fs/create-temp-dir))
          manifest {:id :hooked :name "H" :description "x"
                    :version "0.1.0" :min-cli "0.1.0"
                    :hook? true
                    :tokens {} :secrets [] :features []
                    :next-steps [{:cmd "x"}]}]
      (spit (fs/file (fs/path stage "c3kit-template.bb"))
            "(System/exit 7)")
      (spit (fs/file (fs/path stage "c3kit-template.edn")) (pr-str manifest))
      (try
        (r/render! stage manifest "x" {} {} "0.1.0-SNAPSHOT")
        (should= :hook-should-have-thrown :did-not-throw)
        (catch clojure.lang.ExceptionInfo e
          (should= :hook (:reason (ex-data e)))))
      (fs/delete-tree stage))))
