(ns c3kit-create.render-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.render :as r]
            [babashka.fs :as fs]))

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
                 (slurp (fs/file (fs/path stage "c3kit-template.edn")))
                 "my-cool-app"
                 {:ssr false}        ; features map
                 {})                  ; db choice (no db markers)
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
