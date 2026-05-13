#!/usr/bin/env bb
;; Usage: bb dev/verify-scaffold.bb --combo <name> [--cli <path>]
;;
;; Reads spec/combos/<combo>.expected.edn, invokes c3kit-create against
;; templates/full-stack-reagent in --template-dir mode, then asserts.

(ns verify-scaffold
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(def opts-spec
  [["-c" "--combo COMBO" "Combo name (matches spec/combos/<combo>.expected.edn)"]
   ["-C" "--cli PATH"   "Path to c3kit-create uberscript"
    :default (str (System/getProperty "user.dir") "/../../cli/dist/c3kit-create.bb")]
   [nil  "--keep-tmp"   "Don't delete scaffold tmp dir on success (for debugging)"]])

(defn fail [msg]
  (println "FAIL:" msg)
  (System/exit 1))

(defn run [{:keys [combo cli keep-tmp]}]
  (when-not combo (fail "missing --combo"))
  (let [script-dir  (System/getProperty "user.dir")
        combo-file  (str script-dir "/spec/combos/" combo ".expected.edn")
        _           (when-not (fs/exists? combo-file)
                      (fail (str "combo file not found: " combo-file)))
        expected    (edn/read-string (slurp combo-file))
        tmp         (str (fs/create-temp-dir {:prefix "verify-scaffold-"}))
        target      (str tmp "/" (:name expected))
        templates-dir (str script-dir "/..")
        feat-flags  (mapcat (fn [[k v]] ["--feature" (str (name k) "=" (boolean v))])
                            (:features expected))
        cli-args    (concat [cli (:name expected)
                             "--template-dir" templates-dir
                             "--template" "full-stack-reagent"
                             "--db" (name (:db expected))
                             "--yes" "--no-git"]
                            feat-flags)
        _           (println "Scaffolding:" (str/join " " (rest cli-args)))
        {:keys [exit out err]} (apply p/sh {:dir tmp} "bb" cli-args)
        _           (println out)
        _           (when (seq err) (println "stderr:" err))
        _           (when-not (zero? exit) (fail (str "CLI exit " exit)))]

    ;; must-exist
    (doseq [p (:must-exist expected)]
      (when-not (fs/exists? (str target "/" p))
        (fail (str "must-exist missing: " p))))

    ;; must-not-exist
    (doseq [p (:must-not-exist expected)]
      (when (fs/exists? (str target "/" p))
        (fail (str "must-not-exist present: " p))))

    ;; file-contains
    (doseq [[filepath strs] (:file-contains expected)
            s strs]
      (let [full-path (str target "/" filepath)]
        (when-not (fs/exists? full-path) (fail (str "file-contains: missing file " filepath)))
        (let [content (slurp full-path)]
          (when-not (str/includes? content s)
            (fail (str "file-contains miss: " filepath " missing " (pr-str s)))))))

    ;; file-not-contains
    (doseq [[filepath strs] (:file-not-contains expected)
            s strs]
      (let [full-path (str target "/" filepath)]
        (when-not (fs/exists? full-path) (fail (str "file-not-contains: missing file " filepath)))
        (let [content (slurp full-path)]
          (when (str/includes? content s)
            (fail (str "file-not-contains hit: " filepath " contained " (pr-str s)))))))

    ;; residue greps for token + marker leakage in src/spec/dev
    ;; exclude the verify-scaffold.bb script itself which contains these strings as literals
    (doseq [pat ["@c3kit/feature" "@c3kit/db"]]
      (let [{:keys [out]} (p/sh "grep" "-rE" "--exclude=verify-scaffold.bb" pat target)]
        (when (seq (str/trim out))
          (println "RESIDUE for" pat ":")
          (doseq [l (str/split-lines out)] (println "  " l))
          (fail (str "residue: " pat)))))

    ;; clojure -M:test:spec inside scaffold (clojure not clj — CI runners may lack rlwrap)
    (println "Running clojure -M:test:spec in scaffold ...")
    (let [{:keys [exit out err]} (p/sh {:dir target} "clojure" "-M:test:spec")]
      (println out)
      (when (seq err) (println err))
      (when-not (zero? exit) (fail "clj -M:test:spec failed in scaffold")))

    (when-not keep-tmp (fs/delete-tree tmp))
    (println "PASS:" combo)))

(let [{:keys [options errors]} (cli/parse-opts *command-line-args* opts-spec)]
  (when errors (fail (str "args: " errors)))
  (run options))
