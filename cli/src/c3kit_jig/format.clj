(ns c3kit-jig.format
  "Post-render code formatting. Runs cljfmt on the scaffolded `.clj`/`.cljc`/
   `.cljs` files AFTER token replacement, feature stripping, and path renames —
   i.e. on clean Clojure with no leftover scaffolder markers. Driven by the
   `cljfmt.edn` the template ships into the scaffold. No-op when the scaffold
   doesn't ship a cljfmt config.

   cljfmt is brought in via `add-deps` so this works both when the CLI runs
   from its own `bb.edn` (deps already on classpath) and when invoked via
   `bb -cp …` (e.g. the verification harness), where `bb.edn` is bypassed.

   cljfmt.core is loaded lazily via `requiring-resolve` (see `format!`), NOT
   `require`d statically here: a static require makes `bb uberscript` inline
   cljfmt.core into the distributed script, and its load-time `read-resource`
   macro then throws (the resource isn't bundled in an uberscript). Resolving
   at call time keeps cljfmt out of the uberscript so it loads from the real
   jar (resources intact) that `add-deps` puts on the classpath at runtime."
  (:require [babashka.deps :as deps]))

(deps/add-deps '{:deps {dev.weavejester/cljfmt {:mvn/version "0.16.4"}}})

(require '[babashka.fs :as fs]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(def ^:private FMT-EXTS #{"clj" "cljc" "cljs"})

(defn- ext-of [^java.io.File f]
  (let [n (.getName f)] (str/lower-case (or (last (str/split n #"\.")) ""))))

(defn- read-cljfmt-config [stage-dir]
  (let [p (fs/path stage-dir "cljfmt.edn")]
    (when (fs/exists? p)
      (try (edn/read-string (slurp (fs/file p)))
           (catch Exception _ nil)))))

(defn- fmt-files [stage-dir]
  (for [sub ["src" "spec" "dev"]
        :let [root (fs/path stage-dir sub)]
        :when (fs/exists? root)
        ^java.io.File f (file-seq (fs/file root))
        :when (and (.isFile f) (FMT-EXTS (ext-of f)))]
    f))

(defn format!
  "Reformat every clj/cljc/cljs file under stage-dir/{src,spec,dev} using
   cljfmt and the options from stage-dir/cljfmt.edn. No-op if no config."
  [stage-dir]
  (when-let [opts (read-cljfmt-config stage-dir)]
    (let [reformat-string (requiring-resolve 'cljfmt.core/reformat-string)]
      (doseq [^java.io.File f (fmt-files stage-dir)]
        (let [orig (slurp f)
              fmt  (reformat-string orig opts)]
          (when-not (= orig fmt)
            (spit f fmt)))))))
