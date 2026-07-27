(ns c3kit-jig.install-sh-spec
  "Exercises install.sh's download-verification gates.

  install.sh has to stay a single self-contained file (users pipe it straight
  from GitHub), so it cannot source a testable library. Instead the functions
  worth testing are wrapped in `# >>> testable >>>` / `# <<< testable <<<`
  markers, and this spec evals just those regions in a bash subshell with curl
  stubbed out — no network, no real installs."
  (:require [speclj.core :refer [describe context it should= should-contain should-not-contain before-all]]
            [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(def ^:private install-sh "install.sh")

(defn- testable-region []
  (let [src     (slurp install-sh)
        regions (map second (re-seq #"(?s)# >>> testable >>>\n(.*?)# <<< testable <<<" src))]
    (when (empty? regions)
      (throw (ex-info "install.sh has no `# >>> testable >>>` regions to extract" {})))
    (str/join "\n" regions)))

;; Stands in for curl: `curl -fsSL <url> -o <dest>` copies
;; $FIXTURE_DIR/<basename of url> to <dest>, and exits 22 (curl's HTTP-error
;; code) when no such fixture exists, so "download failed" paths are reachable.
(def ^:private curl-stub
  "curl() {
     local url='' dest=''
     while [[ $# -gt 0 ]]; do
       case \"$1\" in
         -o) dest=\"$2\"; shift 2 ;;
         -*) shift ;;
         *)  url=\"$1\"; shift ;;
       esac
     done
     local fixture=\"$FIXTURE_DIR/$(basename \"$url\")\"
     [[ -f \"$fixture\" ]] || return 22
     if [[ -n \"$dest\" ]]; then cp \"$fixture\" \"$dest\"; else cat \"$fixture\"; fi
   }")

(def ^:private fixtures (atom nil))

(defn- write-fixture! [name content]
  (spit (fs/file (fs/path @fixtures name)) content))

(defn- sha256-of [content]
  (-> (p/shell {:out :string :in content} "shasum" "-a" "256" "-")
      :out
      (str/split #"\s+")
      first))

(defn- run-bash
  "Evals install.sh's testable functions plus `body` in bash. Returns {:exit :out}."
  [body]
  (let [script (str/join "\n" ["set -uo pipefail"
                               (testable-region)
                               curl-stub
                               body])
        res    (p/shell {:out :string :err :string :continue true
                         :extra-env {"FIXTURE_DIR" (str @fixtures)}}
                        "bash" "-c" script)]
    {:exit (:exit res) :out (str (:out res) (:err res))}))

(describe "install.sh download verification"

  (before-all (reset! fixtures (fs/create-temp-dir)))

  (context "fetch_verified"

    (it "accepts a download whose sha256 matches the expected digest"
      (write-fixture! "payload" "hello\n")
      (let [{:keys [exit out]} (run-bash (str "fetch_verified https://example.test/payload \"$FIXTURE_DIR/out\" "
                                              (sha256-of "hello\n")
                                              " && echo VERIFIED && cat \"$FIXTURE_DIR/out\""))]
        (should= 0 exit)
        (should-contain "VERIFIED" out)
        (should-contain "hello" out)))

    (it "rejects a download whose sha256 does not match"
      (write-fixture! "payload" "hello\n")
      (let [{:keys [exit out]} (run-bash (str "fetch_verified https://example.test/payload \"$FIXTURE_DIR/out\" "
                                              "0000000000000000000000000000000000000000000000000000000000000000"
                                              " || echo REJECTED"))]
        (should= 0 exit)
        (should-contain "checksum mismatch" out)
        (should-contain "REJECTED" out)))

    (it "refuses to trust a download when no expected digest was found"
      ;; Guards the case where parsing a checksums file yields nothing: an empty
      ;; expected value must fail closed rather than compare equal to nothing.
      (write-fixture! "payload" "hello\n")
      (let [{:keys [exit out]} (run-bash "fetch_verified https://example.test/payload \"$FIXTURE_DIR/out\" \"\" || echo REJECTED")]
        (should= 0 exit)
        (should-contain "no expected sha256" out)
        (should-contain "REJECTED" out)))

    (it "reports a failed download without claiming verification"
      (let [{:keys [exit out]} (run-bash "fetch_verified https://example.test/absent \"$FIXTURE_DIR/out\" abc || echo REJECTED")]
        (should= 0 exit)
        (should-contain "download failed" out)
        (should-contain "REJECTED" out)
        (should-not-contain "checksum mismatch" out))))

  (context "checksum_for"

    (it "reads the digest for an exact filename match"
      (write-fixture! "checksums.txt"
                      (str "aaaa  gum_0.17.0_Darwin_arm64.tar.gz\n"
                           "bbbb  gum_0.17.0_Linux_x86_64.tar.gz\n"))
      (let [{:keys [exit out]} (run-bash "checksum_for \"$FIXTURE_DIR/checksums.txt\" gum_0.17.0_Linux_x86_64.tar.gz")]
        (should= 0 exit)
        (should= "bbbb" (str/trim out))))

    (it "does not confuse a tarball with same-prefix sidecar files"
      ;; goreleaser publishes gum_X_Linux_x86_64.tar.gz.sbom.json next to the
      ;; tarball; a substring match would pick up the wrong digest.
      (write-fixture! "checksums.txt"
                      (str "cccc  gum_0.17.0_Linux_x86_64.tar.gz.sbom.json\n"
                           "bbbb  gum_0.17.0_Linux_x86_64.tar.gz\n"))
      (let [{:keys [exit out]} (run-bash "checksum_for \"$FIXTURE_DIR/checksums.txt\" gum_0.17.0_Linux_x86_64.tar.gz")]
        (should= 0 exit)
        (should= "bbbb" (str/trim out))))

    (it "fails when the filename is absent instead of returning an empty digest"
      (write-fixture! "checksums.txt" "aaaa  gum_0.17.0_Darwin_arm64.tar.gz\n")
      (let [{:keys [exit out]} (run-bash "checksum_for \"$FIXTURE_DIR/checksums.txt\" gum_0.17.0_Linux_arm64.tar.gz || echo ABSENT")]
        (should= 0 exit)
        (should-contain "ABSENT" out)
        (should-not-contain "aaaa" out))))

  (context "temp-dir hygiene"

    (it "make_temp_dir creates the dir under TMPDIR and cleanup removes it"
      ;; Returns the path in $REPLY on purpose: capturing it as
      ;; a="$(make_temp_dir)" would register the dir in a subshell, leaving the
      ;; parent's registry empty and cleanup with nothing to sweep.
      (let [{:keys [exit out]} (run-bash (str "export TMPDIR=\"$FIXTURE_DIR/space1\"; mkdir -p \"$TMPDIR\"\n"
                                              "make_temp_dir; a=\"$REPLY\"\n"
                                              "make_temp_dir; b=\"$REPLY\"\n"
                                              "[[ -d \"$a\" && -d \"$b\" && \"$a\" != \"$b\" ]] && echo TWO_DIRS\n"
                                              "case \"$a\" in \"$TMPDIR\"/*) echo UNDER_TMPDIR ;; esac\n"
                                              "cleanup\n"
                                              "echo \"remaining=$(find \"$TMPDIR\" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')\""))]
        (should= 0 exit)
        (should-contain "TWO_DIRS" out)
        (should-contain "UNDER_TMPDIR" out)
        (should-contain "remaining=0" out)))

    (it "cleanup is safe to call when nothing was ever created"
      (let [{:keys [exit out]} (run-bash "cleanup && cleanup && echo IDEMPOTENT")]
        (should= 0 exit)
        (should-contain "IDEMPOTENT" out)))

    (it "sweeps temp dirs on exit from inside a function"
      ;; A `trap … RETURN` never fires when the function exits the script
      ;; instead of returning — sha256() does exactly that when no digest tool
      ;; exists. The EXIT trap is the backstop.
      (let [{:keys [exit out]} (run-bash (str "export TMPDIR=\"$FIXTURE_DIR/space2\"; mkdir -p \"$TMPDIR\"\n"
                                              "doomed() { make_temp_dir; echo \"$REPLY\" > \"$TMPDIR/../doomed_path\"; exit 7; }\n"
                                              "doomed"))]
        (should= 7 exit)
        (should= "" out)
        (let [leaked (str/trim (slurp (fs/file (fs/path @fixtures "doomed_path"))))]
          (should= false (fs/exists? leaked)))))

    (it "install_c3kit_jig leaves no temp dir behind on success"
      (write-fixture! "latest" "{\n  \"tag_name\": \"9.9.9\"\n}")
      (write-fixture! "c3kit-jig.bb" "#!/usr/bin/env bb\n(println :ok)\n")
      (write-fixture! "c3kit-jig.bb.sha256" (str (sha256-of "#!/usr/bin/env bb\n(println :ok)\n") "\n"))
      (let [{:keys [exit out]} (run-bash (str "export TMPDIR=\"$FIXTURE_DIR/space3\"; mkdir -p \"$TMPDIR\"\n"
                                              "INSTALL_DIR=\"$FIXTURE_DIR/bin4\"; mkdir -p \"$INSTALL_DIR\"\n"
                                              "REPO=cleancoders/c3kit-jig; BIN_NAME=c3kit-jig\n"
                                              "install_c3kit_jig && echo INSTALLED\n"
                                              "echo \"remaining=$(find \"$TMPDIR\" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')\""))]
        (should= 0 exit)
        (should-contain "INSTALLED" out)
        (should-contain "remaining=0" out)))

    (it "install_c3kit_jig leaves no rejected artifact behind on mismatch"
      (write-fixture! "latest" "{\n  \"tag_name\": \"9.9.9\"\n}")
      (write-fixture! "c3kit-jig.bb" "#!/usr/bin/env bb\n(println :tampered)\n")
      (write-fixture! "c3kit-jig.bb.sha256"
                      "0000000000000000000000000000000000000000000000000000000000000000\n")
      (let [{:keys [exit out]} (run-bash (str "export TMPDIR=\"$FIXTURE_DIR/space4\"; mkdir -p \"$TMPDIR\"\n"
                                              "INSTALL_DIR=\"$FIXTURE_DIR/bin5\"; mkdir -p \"$INSTALL_DIR\"\n"
                                              "REPO=cleancoders/c3kit-jig; BIN_NAME=c3kit-jig\n"
                                              "install_c3kit_jig || echo ABORTED\n"
                                              "echo \"remaining=$(find \"$TMPDIR\" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')\""))]
        (should= 0 exit)
        (should-contain "ABORTED" out)
        (should-contain "remaining=0" out)))

    (it "install_gum leaves no temp dir behind when verification fails"
      (write-fixture! "latest" "{\n  \"tag_name\": \"v0.17.0\"\n}")
      (write-fixture! "checksums.txt" "0000  gum_0.17.0_Linux_x86_64.tar.gz\n")
      (write-fixture! "gum_0.17.0_Linux_x86_64.tar.gz" "not-a-real-tarball\n")
      (let [{:keys [exit out]} (run-bash (str "export TMPDIR=\"$FIXTURE_DIR/space5\"; mkdir -p \"$TMPDIR\"\n"
                                              "INSTALL_DIR=\"$FIXTURE_DIR/bin6\"; mkdir -p \"$INSTALL_DIR\"\n"
                                              "uname_s=Linux; uname() { echo x86_64; }\n"
                                              "install_gum || echo SKIPPED_GUM\n"
                                              "echo \"remaining=$(find \"$TMPDIR\" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')\""))]
        (should= 0 exit)
        (should-contain "SKIPPED_GUM" out)
        (should-contain "remaining=0" out))))

  (context "find_first"

    (it "returns the first matching file without a pipeline"
      ;; `find … | head -1` is the same defect class as the tag_name pipeline:
      ;; head exits on the first line and find dies of EPIPE under pipefail.
      (let [{:keys [exit out]} (run-bash (str "d=\"$FIXTURE_DIR/tree\"; mkdir -p \"$d/nested\"\n"
                                              "printf x > \"$d/nested/gum\"\n"
                                              "find_first \"$d\" gum"))]
        (should= 0 exit)
        (should-contain "nested/gum" out)))

    (it "fails when no file matches"
      (let [{:keys [exit out]} (run-bash (str "d=\"$FIXTURE_DIR/empty-tree\"; mkdir -p \"$d\"\n"
                                              "find_first \"$d\" gum || echo NOT_FOUND"))]
        (should= 0 exit)
        (should-contain "NOT_FOUND" out))))

  (context "latest_tag"

    (it "reads tag_name out of a pretty-printed release payload"
      (write-fixture! "latest" "{\n  \"id\": 1,\n  \"tag_name\": \"0.1.1\",\n  \"name\": \"0.1.1\"\n}")
      (let [{:keys [exit out]} (run-bash "latest_tag https://example.test/latest")]
        (should= 0 exit)
        (should= "0.1.1" (str/trim out))))

    (it "reads tag_name out of a compact single-line payload"
      (write-fixture! "latest" "{\"url\":\"https://x\",\"id\":1,\"tag_name\":\"0.2.0\",\"draft\":false}")
      (let [{:keys [exit out]} (run-bash "latest_tag https://example.test/latest")]
        (should= 0 exit)
        (should= "0.2.0" (str/trim out))))

    (it "handles a payload larger than the pipe buffer"
      ;; Regression: piping curl into `grep -m1` let grep exit on the first
      ;; match, curl then died of EPIPE (exit 56), and pipefail turned that into
      ;; a silent "release unavailable" for any payload big enough to block on
      ;; write — which is exactly what gum's release JSON does.
      (write-fixture! "latest"
                      (str "{\n  \"tag_name\": \"v0.17.0\",\n  \"body\": \""
                           (str/join (repeat 200000 "x"))
                           "\"\n}"))
      (let [{:keys [exit out]} (run-bash "latest_tag https://example.test/latest")]
        (should= 0 exit)
        (should= "v0.17.0" (str/trim out))))

    (it "fails when the release payload cannot be fetched"
      (let [{:keys [exit out]} (run-bash "latest_tag https://example.test/absent || echo NO_TAG")]
        (should= 0 exit)
        (should-contain "NO_TAG" out))))

  (context "install_c3kit_jig"

    (it "installs the uberscript when it matches the published sha256"
      (write-fixture! "latest" "{\"tag_name\": \"9.9.9\"}")
      (write-fixture! "c3kit-jig.bb" "#!/usr/bin/env bb\n(println :hi)\n")
      (write-fixture! "c3kit-jig.bb.sha256" (str (sha256-of "#!/usr/bin/env bb\n(println :hi)\n") "\n"))
      (let [{:keys [exit out]} (run-bash (str "INSTALL_DIR=\"$FIXTURE_DIR/bin\"; mkdir -p \"$INSTALL_DIR\"\n"
                                              "REPO=cleancoders/c3kit-jig; BIN_NAME=c3kit-jig\n"
                                              "install_c3kit_jig && echo INSTALLED\n"
                                              "test -x \"$INSTALL_DIR/c3kit-jig\" && echo EXECUTABLE"))]
        (should= 0 exit)
        (should-contain "INSTALLED" out)
        (should-contain "EXECUTABLE" out)))

    (it "aborts and installs nothing when the sha256 does not match"
      (write-fixture! "latest" "{\"tag_name\": \"9.9.9\"}")
      (write-fixture! "c3kit-jig.bb" "#!/usr/bin/env bb\n(println :tampered)\n")
      (write-fixture! "c3kit-jig.bb.sha256"
                      "0000000000000000000000000000000000000000000000000000000000000000\n")
      (let [{:keys [exit out]} (run-bash (str "INSTALL_DIR=\"$FIXTURE_DIR/bin2\"; mkdir -p \"$INSTALL_DIR\"\n"
                                              "REPO=cleancoders/c3kit-jig; BIN_NAME=c3kit-jig\n"
                                              "install_c3kit_jig || echo ABORTED\n"
                                              "test -e \"$INSTALL_DIR/c3kit-jig\" && echo LEFT_BEHIND || echo CLEAN"))]
        (should= 0 exit)
        (should-contain "checksum mismatch" out)
        (should-contain "ABORTED" out)
        (should-contain "CLEAN" out)
        (should-not-contain "LEFT_BEHIND" out))))

  (context "install_gum"

    (it "is best-effort: a checksum mismatch skips gum without killing the install"
      (write-fixture! "latest" "{\"tag_name\": \"v0.17.0\"}")
      (write-fixture! "checksums.txt" "0000  gum_0.17.0_Linux_x86_64.tar.gz\n")
      (write-fixture! "gum_0.17.0_Linux_x86_64.tar.gz" "not-a-real-tarball\n")
      (let [{:keys [exit out]} (run-bash (str "INSTALL_DIR=\"$FIXTURE_DIR/bin3\"; mkdir -p \"$INSTALL_DIR\"\n"
                                              "uname_s=Linux\n"
                                              "uname() { echo x86_64; }\n"
                                              "install_gum || echo SKIPPED_GUM\n"
                                              "echo INSTALL_CONTINUED"))]
        (should= 0 exit)
        (should-contain "SKIPPED_GUM" out)
        (should-contain "INSTALL_CONTINUED" out)
        (should-not-contain "installed gum" out)))))
