(ns c3kit-jig.args-spec
  (:require [speclj.core :refer [describe it should= should should-contain]]
            [c3kit-jig.args :as args]))

(describe "args/parse"
          (describe "subcommands"
                    (it "no args → help"
                        (should= :help (:action (args/parse []))))

                    (it "help subcommand and --help flag"
                        (should= :help (:action (args/parse ["help"])))
                        (should= :help (:action (args/parse ["--help"]))))

                    (it "version subcommand and --version flag"
                        (should= :version (:action (args/parse ["version"])))
                        (should= :version (:action (args/parse ["--version"]))))

                    (it "list subcommand"
                        (should= :list (:action (args/parse ["list"]))))

                    (it "upgrade subcommand"
                        (should= :upgrade (:action (args/parse ["upgrade"]))))

                    (it "create subcommand → :scaffold action"
                        (should= :scaffold (:action (args/parse ["create"]))))

                    (it "unknown subcommand → :error"
                        (let [r (args/parse ["frobnicate"])]
                          (should= :error (:action r))
                          (should-contain "Unknown subcommand" (:error r)))))

          (describe "create subcommand options"
                    (it "captures positional name"
                        (let [r (args/parse ["create" "my-app"])]
                          (should= "my-app" (:name (:options r)))))

                    (it "captures --template / -t"
                        (let [r (args/parse ["create" "my-app" "--template" "fe-vanilla"])]
                          (should= "fe-vanilla" (:template (:options r))))
                        (let [r (args/parse ["create" "my-app" "-t" "fe-vanilla"])]
                          (should= "fe-vanilla" (:template (:options r)))))

                    (it "captures --yes / -y"
                        (should (:yes (:options (args/parse ["create" "my-app" "--yes"]))))
                        (should (:yes (:options (args/parse ["create" "my-app" "-y"])))))

                    (it "captures --no-git as :git? false"
                        (should= false (:git? (:options (args/parse ["create" "my-app" "--no-git"])))))

                    (it "defaults --git? true"
                        (should= true (:git? (:options (args/parse ["create" "my-app"])))))

                    (it "captures --template-dir"
                        (should= "/tmp/x" (:template-dir (:options (args/parse ["create" "my-app" "--template-dir" "/tmp/x"])))))

                    (it "captures --target-parent"
                        (should= "/tmp/xyz"
                                 (:target-parent (:options (args/parse ["create" "my-app" "--target-parent" "/tmp/xyz"])))))

                    (it "captures --db as a keyword"
                        (should= :sqlite
                                 (:db (:options (args/parse ["create" "my-app" "--db" "sqlite"])))))

                    (it "captures repeated --feature flags into a {id-kw bool} map"
                        (let [r (args/parse ["create" "my-app" "--feature" "auth=false" "--feature" "csp=true"])]
                          (should= {:auth false :csp true} (:feature (:options r)))))

                    (it "--feature accepts yes/no/y/n/1/0 in addition to true/false"
                        (let [r (args/parse ["create" "my-app" "--feature" "a=yes" "--feature" "b=no"
                                             "--feature" "c=1"   "--feature" "d=0"])]
                          (should= {:a true :b false :c true :d false} (:feature (:options r)))))

                    (it "rejects malformed --feature"
                        (let [r (args/parse ["create" "my-app" "--feature" "nope"])]
                          (should= :error (:action r))))

                    (it "reports usage errors for create"
                        (let [r (args/parse ["create" "--no-such-flag"])]
                          (should= :error (:action r))
                          (should-contain "Unknown option" (:error r)))))

          (describe "help text"
                    (it "help mentions c3kit-jig"
                        (should-contain "c3kit-jig" (args/help)))
                    (it "help mentions create subcommand"
                        (should-contain "create" (args/help)))))
