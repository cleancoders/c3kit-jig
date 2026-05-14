#!/usr/bin/env bb
;; Isolation test for c3kit-template.bb hook.
;; Two scenarios:
;;   1. Clean scaffold → exit 0
;;   2. Scaffold with residual @c3kit marker → exit 4 + path printed

(require '[babashka.fs :as fs])
(require '[babashka.process :as p])
(require '[clojure.string :as str])

(def hook (str (System/getProperty "user.dir") "/c3kit-template.bb"))

;; --- Case 1: clean ---
(let [tmp (str (fs/create-temp-dir {:prefix "hook-test-clean-"}))]
  (fs/create-dirs (str tmp "/src/clj/my_app"))
  (spit (str tmp "/src/clj/my_app/main.clj") "(ns my_app.main)")
  (let [{:keys [exit]} (p/sh "bb" hook tmp)]
    (assert (zero? exit) (str "clean scaffold should exit 0, got " exit)))
  (fs/delete-tree tmp))

;; --- Case 2: residual marker ---
(let [tmp (str (fs/create-temp-dir {:prefix "hook-test-residue-"}))]
  (fs/create-dirs (str tmp "/src/clj/my_app"))
  (spit (str tmp "/src/clj/my_app/main.clj")
        ";; @c3kit/feature :auth = [my_app.auth.x :as x]\n(ns my_app.main)")
  (let [{:keys [exit out]} (p/sh "bb" hook tmp)]
    (assert (= 4 exit) (str "residue scaffold should exit 4, got " exit))
    (assert (str/includes? out "main.clj")
            "stdout should list the offending file"))
  (fs/delete-tree tmp))

(println "PASS")
