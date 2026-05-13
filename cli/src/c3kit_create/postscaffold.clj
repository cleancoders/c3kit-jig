(ns c3kit-create.postscaffold
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [c3kit-create.ui :as ui]))

(defn git-init!
  "Run `git init -b main && git add -A && git commit -m \"chore: initial scaffold\"`."
  [project-dir]
  (let [opts {:dir project-dir :out :string :err :string}]
    (doseq [args [["git" "init" "-b" "main"]
                  ["git" "add" "-A"]
                  ["git" "commit" "-m" "chore: initial scaffold"]]]
      (let [res (apply p/shell (concat [(merge opts {:continue true})] args))]
        (when-not (zero? (:exit res))
          (throw (ex-info (str "git step failed: " args " — " (:err res))
                          {:postscaffold? true})))))))

(defn install!
  "Run `clj -P` and `npm install` if applicable. Honors :dry-run? for tests."
  [project-dir {:keys [install dry-run?]}]
  (when install
    (when-not dry-run?
      (p/shell {:dir project-dir} "clj" "-P")
      (when (fs/exists? (fs/path project-dir "package.json"))
        (p/shell {:dir project-dir} "npm" "install")))
    (ui/ok "installed dependencies")))
