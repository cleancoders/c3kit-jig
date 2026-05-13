(ns c3kit-create.args-spec
  (:require [speclj.core :refer [describe it should= should should-contain]]
            [c3kit-create.args :as args]))

(describe "args/parse"
  (it "captures positional name"
    (let [r (args/parse ["my-app"])]
      (should= "my-app" (:name (:options r)))))

  (it "captures --template / -t"
    (let [r (args/parse ["my-app" "--template" "fe-vanilla"])]
      (should= "fe-vanilla" (:template (:options r))))
    (let [r (args/parse ["my-app" "-t" "fe-vanilla"])]
      (should= "fe-vanilla" (:template (:options r)))))

  (it "captures --yes / -y"
    (should (:yes (:options (args/parse ["my-app" "--yes"]))))
    (should (:yes (:options (args/parse ["my-app" "-y"])))))

  (it "captures --no-git as :git? false"
    (should= false (:git? (:options (args/parse ["my-app" "--no-git"])))))

  (it "defaults --git? true"
    (should= true (:git? (:options (args/parse ["my-app"])))))

  (it "captures --template-dir + env fallback"
    (should= "/tmp/x" (:template-dir (:options (args/parse ["my-app" "--template-dir" "/tmp/x"])))))

  (it "captures action flags"
    (should= :version  (:action (args/parse ["--version"])))
    (should= :help     (:action (args/parse ["--help"])))
    (should= :list     (:action (args/parse ["--list"])))
    (should= :upgrade  (:action (args/parse ["--upgrade"])))
    (should= :scaffold (:action (args/parse ["my-app"])))
    (should= :scaffold (:action (args/parse []))))

  (it "reports usage errors"
    (let [r (args/parse ["--no-such-flag"])]
      (should= :error (:action r))
      (should-contain "Unknown option" (:error r))))

  (it "includes a help string"
    (should-contain "c3kit-create" (args/help)))

  (it "captures --target-parent"
    (should= "/tmp/xyz"
             (:target-parent (:options (args/parse ["my-app" "--target-parent" "/tmp/xyz"])))))

  (it "captures --db as a keyword"
    (should= :sqlite
             (:db (:options (args/parse ["my-app" "--db" "sqlite"])))))

  (it "captures repeated --feature flags into a {id-kw bool} map"
    (let [r (args/parse ["my-app" "--feature" "auth=false" "--feature" "csp=true"])]
      (should= {:auth false :csp true} (:feature (:options r)))))

  (it "--feature accepts yes/no/y/n/1/0 in addition to true/false"
    (let [r (args/parse ["my-app" "--feature" "a=yes" "--feature" "b=no"
                          "--feature" "c=1"   "--feature" "d=0"])]
      (should= {:a true :b false :c true :d false} (:feature (:options r)))))

  (it "rejects malformed --feature"
    (let [r (args/parse ["my-app" "--feature" "nope"])]
      (should= :error (:action r)))))
