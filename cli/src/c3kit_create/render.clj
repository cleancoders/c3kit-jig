(ns c3kit-create.render
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [c3kit-create.features :as f]
            [c3kit-create.hook :as hook]
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

(defn- replace-secrets! [secret-map file]
  (when (text-file? file)
    (let [s  (slurp file)
          s' (sec/apply-secret-map s secret-map)]
      (when-not (= s s')
        (spit file s')))))

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

(defn- resolve-delete-path [stage-dir tokens user path]
  (let [with-placeholders (reduce (fn [s [token-name _]]
                                    (str/replace s
                                                 (str "{{" token-name "}}")
                                                 (:underscore user)))
                                  path tokens)
        renamed (rn/replace-many with-placeholders tokens user)]
    (str (fs/path stage-dir renamed))))

(defn- apply-deletes! [stage-dir manifest features user]
  (let [tokens (:tokens manifest)]
    (doseq [feat (:features manifest)
            :let [id  (:id feat)
                  on? (get features id (:default feat))]
            :when (not on?)
            path (:delete-when-off feat)
            :let [full (resolve-delete-path stage-dir tokens user path)]]
      (when (fs/exists? full)
        (if (fs/directory? full)
          (fs/delete-tree full)
          (fs/delete full))))))

(defn- rename-readme! [stage-dir]
  (let [src (fs/path stage-dir "README.scaffold.md")
        tgt (fs/path stage-dir "README.md")]
    (when (fs/exists? src)
      (fs/delete-if-exists tgt)
      (fs/move src tgt))))

(defn- write-context! [stage-dir manifest user-name name-variants
                       db-choice features secret-map cli-version]
  (let [context {:name             user-name
                 :name-variants    name-variants
                 :db               (:db db-choice)
                 :features         features
                 :secrets          (mapv (fn [{:keys [placeholder]}]
                                           {:placeholder placeholder
                                            :generated   (get secret-map placeholder)})
                                         (:secrets manifest))
                 :template         (:id manifest)
                 :template-version (:version manifest)
                 :cli-version      cli-version}]
    (spit (fs/file (fs/path stage-dir ".c3kit-create-context.edn"))
          (pr-str context))))

(defn render!
  "In-place rewrite of `stage-dir`:
   1. tokens + markers, 2. secrets, 3. path renames,
   4. README.scaffold.md → README.md, 5. write .c3kit-create-context.edn,
   6. invoke template hook (if :hook? manifest), 7. cleanup hook + manifest files.
   Caller validates manifest first."
  [stage-dir manifest user-name features db-choice cli-version]
  (let [tokens     (:tokens manifest)
        user       (rn/variants user-name)
        secret-map (sec/generate-secret-map (:secrets manifest))]
    (doseq [file (visit-all-files stage-dir)]
      (rewrite-content! tokens user features db-choice file))
    (doseq [file (visit-all-files stage-dir)]
      (replace-secrets! secret-map file))
    (rename-paths! tokens user stage-dir)
    (apply-deletes! stage-dir manifest features user)
    (rename-readme! stage-dir)
    (write-context! stage-dir manifest user-name user db-choice features
                    secret-map cli-version)
    (when (:hook? manifest) (hook/run! stage-dir))
    (fs/delete-if-exists (fs/path stage-dir ".c3kit-create-context.edn"))
    (fs/delete-if-exists (fs/path stage-dir "c3kit-template.edn"))
    (fs/delete-if-exists (fs/path stage-dir "c3kit-template.bb"))
    stage-dir))
