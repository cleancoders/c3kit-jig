(ns c3kit-jig.fetch
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(defn list-local-templates
  "Return [{:id \"tiny-fixture\" :label \"Tiny Fixture\" :description \"...\"} ...]
   for every subdir of `templates-root` containing a c3kit-template.edn."
  [templates-root]
  (when (and templates-root (fs/exists? templates-root))
    (->> (fs/list-dir templates-root)
         (filter fs/directory?)
         (keep (fn [d]
                 (let [edn-file (fs/path d "c3kit-template.edn")]
                   (when (fs/exists? edn-file)
                     (let [m (try (edn/read-string (slurp (fs/file edn-file)))
                                  (catch Exception _ nil))]
                       (when m
                         {:id          (fs/file-name d)
                          :label       (or (:name m) (fs/file-name d))
                          :description (:description m)}))))))
         (sort-by :id)
         vec)))

(def ^:private cruft-names
  #{".cpcache" "target" "node_modules" ".idea" ".DS_Store"
    ".nrepl-port" ".specljs-timestamp" "prerender" "prerendered"})

(def ^:private cruft-exts #{"iml" "class" "jar"})

(defn prune-cruft!
  "Delete known dev/VCS artifacts from a freshly copied template tree.
   Matches directory/file basenames exactly (cruft-names) plus build-artifact
   extensions (cruft-exts) — never substring/suffix, to avoid deleting
   legitimately-named template files."
  [dest]
  (doseq [^java.io.File f (reverse (file-seq (fs/file dest)))
          :when (.exists f)
          :let  [n (.getName f)
                 ext (last (str/split n #"\."))]
          :when (or (contains? cruft-names n)
                    (and (.isFile f) (contains? cruft-exts ext)))]
    (if (.isDirectory f)
      (fs/delete-tree (str f))
      (fs/delete-if-exists (str f)))))

(defn from-local-dir
  "Copy `<templates-root>/<id>/` into `dest`. Throws if missing."
  [templates-root id dest]
  (let [src (fs/path templates-root id)]
    (when-not (fs/exists? src)
      (throw (ex-info (str "template not found: " src) {:fetch? true})))
    (fs/create-dirs (fs/parent dest))
    (fs/copy-tree src dest)
    (prune-cruft! dest)
    dest))

(defn- git-available? []
  (try
    (zero? (:exit (p/shell {:out :string :err :string} "git" "--version")))
    (catch Exception _ false)))

(defn clone-repo!
  "Shallow-clone `repo-url` at `ref` into `<work-dir>/clone`. Returns the path
   to the clone's templates/ subdir. Throws ex-info on failure."
  [repo-url ref work-dir]
  (when-not (git-available?)
    (throw (ex-info "git not on PATH" {:fetch? true :reason :no-git})))
  (let [clone (fs/path work-dir "clone")
        res   (p/shell {:dir (str work-dir) :continue true}
                       "git" "clone" "--depth" "1"
                       "--branch" ref repo-url (str clone))]
    (when-not (zero? (:exit res))
      (throw (ex-info (str "git clone failed: " (:err res))
                      {:fetch? true :reason :clone})))
    (str (fs/path clone "templates"))))

(defn from-git
  "Clone monorepo at `ref` into tmp, copy out templates/<id>/, leave tmp alone
   (caller cleans up via the surrounding stage dir)."
  [repo-url ref id work-dir dest]
  (let [templates-dir (clone-repo! repo-url ref work-dir)]
    (from-local-dir templates-dir id dest)))
