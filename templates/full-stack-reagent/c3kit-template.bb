#!/usr/bin/env bb
;; Post-scaffold safety net. Asserts no @c3kit/(feature|db) markers survived
;; scaffolding. Exits non-zero with paths printed if any residue is found.

(ns c3kit-template
  (:require [clojure.string :as str]))

(def scaffold-dir
  (or (first *command-line-args*)
      (do (println "ERROR: scaffold dir required as arg 1")
          (System/exit 1))))

(def text-exts #{".clj" ".cljc" ".cljs" ".edn" ".md" ".html" ".js" ".yml"})
(def marker    #"@c3kit/(feature|db)\s+!?:?\S*\s*[{}=]")

(let [residues (atom [])]
  (doseq [^java.io.File f (file-seq (java.io.File. scaffold-dir))
          :when (and (.isFile f)
                     (not (str/includes? (.getPath f) "/.git/"))
                     (some #(str/ends-with? (.getName f) %) text-exts))]
    (when (re-find marker (slurp f))
      (swap! residues conj (.getPath f))))
  (when (seq @residues)
    (println "ERROR: residual @c3kit markers found in:")
    (doseq [p @residues] (println "  " p))
    (System/exit 4)))

(println "Hook OK — no residual markers.")
