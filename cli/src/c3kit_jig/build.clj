(ns c3kit-jig.build
  "Build the distributable uberscript, baking the root VERSION into it."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]))

(def ^:private out "dist/c3kit-jig.bb")
(def ^:private version-placeholder "0.0.0-DEV")

(defn build!
  "Build `dist/c3kit-jig.bb` and bake the root VERSION into it, replacing the
   `0.0.0-DEV` placeholder. Run from the `cli/` directory. Returns the version."
  []
  (let [version (str/trim (slurp "../VERSION"))]
    (fs/create-dirs "dist")
    (fs/delete-if-exists out)
    (process/shell "bb" "uberscript" out "-m" "c3kit-jig.main")
    (spit out (str "#!/usr/bin/env bb\n" (str/replace (slurp out) version-placeholder version)))
    (fs/set-posix-file-permissions out "rwxr-xr-x")
    (println (str "built " out " @ " version))
    version))
