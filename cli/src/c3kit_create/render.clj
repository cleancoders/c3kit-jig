(ns c3kit-create.render
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [c3kit-create.features :as f]
            [c3kit-create.rename :as rn]
            [c3kit-create.secrets :as sec]))

(def ^:private TEXT-EXTS
  #{"clj" "cljs" "cljc" "edn" "md" "html" "css" "js" "ts" "json"
    "yml" "yaml" "txt" "env" "properties" "xml" "toml" "csv"
    "sh" "bash" "zsh" "gitignore" "kts"})

(defn- text-file? [^java.io.File f]
  (let [n (.getName f)]
    (or (TEXT-EXTS (str/lower-case (or (last (str/split n #"\.")) "")))
        (#{"Dockerfile" "Makefile" "LICENSE"} n))))

(defn- visit-all-files [dir]
  (->> (file-seq (fs/file dir))
       (filter #(.isFile %))))

(defn- rewrite-content! [tokens user features db file]
  (when (text-file? file)
    (let [orig (slurp file)
          after-tokens   (rn/replace-many orig tokens user)
          after-features (f/strip after-tokens features db)]
      (when-not (= orig after-features)
        (spit file after-features)))))

(defn- rename-paths! [tokens user dir]
  ;; depth-first so leaves are renamed before parents.
  (let [paths (->> (file-seq (fs/file dir))
                   (sort-by #(- (count (.getAbsolutePath ^java.io.File %)))))]
    (doseq [^java.io.File p paths]
      (let [old-name (.getName p)
            new-name (rn/replace-many old-name tokens user)]
        (when (not= old-name new-name)
          (fs/move (.getAbsolutePath p)
                   (.getAbsolutePath (java.io.File. (.getParentFile p) new-name))))))))

(defn render!
  "In-place rewrite of `stage-dir`: rename tokens, strip markers, generate secrets,
   rename file/dir paths, drop the manifest file. Caller validates manifest first."
  [stage-dir manifest-edn user-name features db-choice]
  (let [m (edn/read-string manifest-edn)
        tokens (:tokens m)
        user   (rn/variants user-name)]
    ;; 1. content rewrite (tokens + features) on every text file
    (doseq [file (visit-all-files stage-dir)]
      (rewrite-content! tokens user features db-choice file))
    ;; 2. secrets pass — second sweep so token rename can't interfere
    (doseq [file (visit-all-files stage-dir)]
      (when (text-file? file)
        (let [s (slurp file)
              s' (sec/replace-placeholders s (:secrets m))]
          (when-not (= s s')
            (spit file s')))))
    ;; 3. drop manifest
    (fs/delete-if-exists (fs/path stage-dir "c3kit-template.edn"))
    (fs/delete-if-exists (fs/path stage-dir "c3kit-template.bb"))
    ;; 4. rename dirs + files
    (rename-paths! tokens user stage-dir)
    stage-dir))
