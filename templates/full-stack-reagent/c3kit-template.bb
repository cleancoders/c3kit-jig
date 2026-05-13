#!/usr/bin/env bb
;; Post-scaffold hook for templates/full-stack-reagent.
;; CLI sub-spec §7.2 stage 3 invokes this script with the scaffold dir as $1.
;; Reads .c3kit-create-context.edn (CLI sub-spec §10 prereq #2), then:
;;   1. Generates bin/db from the selected :db (token-renamed).
;;   2. Reconciles the HEAD-default :bucket lines in config.clj.
;;   3. Drops :seed alias from deps.edn when :auth is off (defense-in-depth).
;;   4. Greps for residual @c3kit markers — exits non-zero if any remain.

(ns c3kit-template
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def scaffold-dir
  (or (first *command-line-args*)
      (do (println "ERROR: scaffold dir required as arg 1")
          (System/exit 1))))

(def context-file (str scaffold-dir "/.c3kit-create-context.edn"))

(when-not (fs/exists? context-file)
  (println "ERROR: context file not found at" context-file)
  (println "This hook expects the CLI to write .c3kit-create-context.edn before invocation (T1 spec §10 prereq #2).")
  (System/exit 2))

(def ctx (edn/read-string (slurp context-file)))
(def selected-db (:db ctx))
(def auth-on? (get-in ctx [:features :auth]))
(def name-underscore (get-in ctx [:name-variants :underscore]))

;; --- 1. Generate bin/db ---

(defn install-bin-db []
  (let [src (str scaffold-dir "/bin/db.template." (name selected-db))
        dst (str scaffold-dir "/bin/db")]
    (when-not (fs/exists? src)
      (println "ERROR: db template not found for" selected-db ":" src)
      (System/exit 3))
    (let [content (-> (slurp src)
                      (str/replace "acme_dev" (str name-underscore "_dev")))]
      (spit dst content)
      (.setExecutable (java.io.File. dst) true))
    ;; remove all bin/db.template.* siblings
    (doseq [f (fs/list-dir (str scaffold-dir "/bin"))
            :let [n (fs/file-name f)]
            :when (str/starts-with? n "db.template.")]
      (fs/delete f))))

;; --- 2. Reconcile config.clj :bucket lines ---

(defn reconcile-bucket-lines []
  (let [config-path (str scaffold-dir "/src/clj/" name-underscore "/config.clj")
        content     (slurp config-path)
        cleaned     (str/replace content
                                 #"\s*:bucket\s+memory-(local|staging|production)\s+;;\s*HEAD default; replaced by line below at scaffold\n"
                                 "\n")]
    (spit config-path cleaned)))

;; --- 3. Drop :seed alias from deps.edn when :auth off (defense-in-depth) ---

(defn maybe-drop-seed-alias []
  (when-not auth-on?
    (let [deps-path (str scaffold-dir "/deps.edn")
          content   (slurp deps-path)
          cleaned   (str/replace content
                                 #"\n\s*:seed\s+\{[^}]+\}"
                                 "")]
      (spit deps-path cleaned))))

;; --- 4. Residue grep ---

(defn grep-residue []
  (let [residues (atom [])]
    (doseq [f (file-seq (java.io.File. scaffold-dir))
            :when (and (.isFile f)
                       (not (str/includes? (.getPath f) "/.git/"))
                       (let [n (.getName f)]
                         (or (str/ends-with? n ".clj")
                             (str/ends-with? n ".cljc")
                             (str/ends-with? n ".cljs")
                             (str/ends-with? n ".edn")
                             (str/ends-with? n ".md")
                             (str/ends-with? n ".html")
                             (str/ends-with? n ".js")
                             (str/ends-with? n ".yml"))))]
      (let [content (slurp f)
            patt    #"@c3kit/(feature|db)\s+!?:?\S*\s*[{}=]"]
        (when (re-find patt content)
          (swap! residues conj (.getPath f)))))
    (when (seq @residues)
      (println "ERROR: residual markers found in:")
      (doseq [p @residues] (println "  " p))
      (System/exit 4))))

(install-bin-db)
(reconcile-bucket-lines)
(maybe-drop-seed-alias)
(grep-residue)

(println "DB script generated for :" (name selected-db))
