(ns c3kit-create.fetch
  (:require [babashka.fs :as fs]
            [babashka.process :as p]))

(defn from-local-dir
  "Copy `<templates-root>/<id>/` into `dest`. Throws if missing."
  [templates-root id dest]
  (let [src (fs/path templates-root id)]
    (when-not (fs/exists? src)
      (throw (ex-info (str "template not found: " src) {:fetch? true})))
    (fs/create-dirs (fs/parent dest))
    (fs/copy-tree src dest)))

(defn- git-available? []
  (try
    (zero? (:exit (p/shell {:out :string :err :string} "git" "--version")))
    (catch Exception _ false)))

(defn from-git
  "Clone monorepo at `ref` into tmp, copy out templates/<id>/, leave tmp alone
   (caller cleans up via the surrounding stage dir)."
  [repo-url ref id work-dir dest]
  (when-not (git-available?)
    (throw (ex-info "git not on PATH" {:fetch? true :reason :no-git})))
  (let [clone (fs/path work-dir "clone")]
    (let [res (p/shell {:dir (str work-dir) :continue true}
                       "git" "clone" "--depth" "1"
                       "--branch" ref repo-url (str clone))]
      (when-not (zero? (:exit res))
        (throw (ex-info (str "git clone failed: " (:err res))
                        {:fetch? true :reason :clone}))))
    (from-local-dir (str (fs/path clone "templates")) id dest)))
