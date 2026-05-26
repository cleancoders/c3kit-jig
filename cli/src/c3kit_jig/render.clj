(ns c3kit-jig.render
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [c3kit-jig.features :as f]
            [c3kit-jig.hook :as hook]
            [c3kit-jig.rename :as rn]
            [c3kit-jig.secrets :as sec]))

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

(defn- segments [^java.io.File f stage-root]
  (let [rel (.relativize (.toPath (fs/file stage-root)) (.toPath f))]
    (->> (iterator-seq (.iterator rel))
         (map str)
         (vec))))

(defn- feature-dir-match? [segs ns-token feature-name]
  (loop [s segs]
    (cond
      (< (count s) 2)                                false
      (and (= (first s) ns-token)
           (= (second s) feature-name))              true
      :else                                          (recur (rest s)))))

(defn- feature-file-match? [segs ns-token feature-name]
  (and (>= (count segs) 2)
       (= (nth segs (- (count segs) 2)) ns-token)
       (some #(= (last segs) (str feature-name "." %))
             ["clj" "cljc" "cljs"])))

(defn- apply-feature-dir-deletes! [stage-dir manifest features]
  (let [ns-token (or (:namespace-token manifest) "acme")]
    (doseq [feat (:features manifest)
            :let [id  (:id feat)
                  on? (get features id (:default feat))]
            :when (not on?)]
      (let [fname (name id)]
        (doseq [^java.io.File f (vec (file-seq (fs/file stage-dir)))
                :when (.exists f)
                :let  [segs (segments f stage-dir)]]
          (when (or (feature-dir-match? segs ns-token fname)
                    (and (.isFile f) (feature-file-match? segs ns-token fname)))
            (if (.isDirectory f)
              (fs/delete-tree (.getAbsolutePath f))
              (fs/delete-if-exists (.getAbsolutePath f)))))))))

(defn- apply-extras-deletes! [stage-dir manifest features]
  (doseq [feat (:features manifest)
          :let [id  (:id feat)
                on? (get features id (:default feat))]
          :when (not on?)
          path (:extras feat)
          :let [full (str (fs/path stage-dir path))]]
    (when (fs/exists? full)
      (if (fs/directory? full)
        (fs/delete-tree full)
        (fs/delete-if-exists full)))))

(defn- apply-db-rename! [stage-dir manifest db-choice]
  (when-let [db-cfg (:db manifest)]
    (when-let [chosen (:db db-choice)]
      (let [tmpl-pattern (:template db-cfg)
            sibling-glob (:sibling-glob db-cfg)]
        (when (and tmpl-pattern sibling-glob)
          (let [chosen-path (str/replace tmpl-pattern "{{db}}" (name chosen))
                src         (fs/path stage-dir chosen-path)
                dst         (fs/path stage-dir "bin" "db")]
            (when (fs/exists? src)
              (fs/delete-if-exists dst)
              (fs/move (str src) (str dst))
              (.setExecutable (fs/file dst) true))
            (doseq [m (fs/glob stage-dir sibling-glob)]
              (fs/delete-if-exists (str m)))))))))

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
    (spit (fs/file (fs/path stage-dir ".c3kit-jig-context.edn"))
          (pr-str context))))

(defn render!
  "In-place rewrite of `stage-dir`:
   1. secrets, 2. tokens + markers, 3. feature-dir deletes, 3.5. :extras deletes,
   3.6. db template rename (bin/db.template.<db> → bin/db, siblings deleted),
   4. path renames, 5. README.scaffold.md → README.md,
   6. write .c3kit-jig-context.edn, 7. invoke template hook (if :hook? manifest),
   8. cleanup hook + manifest files.
   Caller validates manifest first."
  [stage-dir manifest user-name features db-choice cli-version]
  (let [tokens     (:tokens manifest)
        user       (rn/variants user-name)
        secret-map (sec/generate-secret-map (:secrets manifest))]
    (doseq [file (visit-all-files stage-dir)]
      (replace-secrets! secret-map file))
    (doseq [file (visit-all-files stage-dir)]
      (rewrite-content! tokens user features db-choice file))
    (apply-feature-dir-deletes! stage-dir manifest features)
    (apply-extras-deletes! stage-dir manifest features)
    (apply-db-rename! stage-dir manifest db-choice)
    (rename-paths! tokens user stage-dir)
    (rename-readme! stage-dir)
    (write-context! stage-dir manifest user-name user db-choice features
                    secret-map cli-version)
    (when (:hook? manifest) (hook/run! stage-dir))
    (fs/delete-if-exists (fs/path stage-dir ".c3kit-jig-context.edn"))
    (fs/delete-if-exists (fs/path stage-dir "c3kit-template.edn"))
    (fs/delete-if-exists (fs/path stage-dir "c3kit-template.bb"))
    stage-dir))
