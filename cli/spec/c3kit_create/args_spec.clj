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
    (should-contain "c3kit-create" (args/help))))
