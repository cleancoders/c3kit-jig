(ns c3kit-verify.engine
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [c3kit-verify.checks :as checks]))

(def ^:private here (System/getProperty "user.dir")) ; verification/ when run via bb task

(defn- descriptor-path [template]
  (str (fs/path here "templates" template "verify.edn")))

(defn- combo-path [template combo]
  (str (fs/path here "templates" template "combos" (str combo ".expected.edn"))))

(defn- read-edn [path] (edn/read-string (slurp path)))

(def ^:private tier-checks
  {:full  #{:no-cruft :combo :residue :ns-hyphen :lint :fmt :clj-clean :cljs-run :server-boot}
   :light #{:no-cruft :combo :residue :ns-hyphen :lint :fmt :clj-clean}})

(defn- feature-flags [features]
  (mapcat (fn [[k v]] ["--feature" (str (name k) "=" (boolean v))]) features))

(defn- scaffold!
  "Invoke the CLI to scaffold one combo into a temp dir; return the scaffold path.
   Cleans up the temp dir if scaffolding fails. Verbose mode prints the CLI
   stdout; otherwise it is captured and only surfaced on failure."
  [{:keys [cli-cp template descriptor combo-edn verbose]}]
  (let [tmp (str (fs/create-temp-dir {:prefix "verify-"}))]
    (try
      (let [templates  (str (fs/absolutize (fs/path here (:cli-templates-dir descriptor))))
            cli-cp     (when cli-cp (str (fs/absolutize (fs/path here cli-cp))))
            args       (concat ["create" (:name combo-edn)
                                "--template-dir" templates
                                "--template" template
                                "--db" (name (:db combo-edn))
                                "--yes" "--no-git"]
                               (feature-flags (:features combo-edn)))
            cli-prefix (if cli-cp ["bb" "-cp" cli-cp "-m" "c3kit-jig.main"] ["bb" "-m" "c3kit-jig.main"])
            {:keys [exit out err]} (apply p/sh {:dir tmp} (concat cli-prefix args))]
        (when verbose
          (println out)
          (when (seq err) (println "stderr:" err)))
        (when-not (zero? exit)
          (when-not verbose
            (println out)
            (when (seq err) (println "stderr:" err)))
          (throw (ex-info (str "CLI scaffold failed, exit " exit) {:exit exit})))
        {:tmp tmp :scaffold (str (fs/path tmp (:name combo-edn)))})
      (catch Throwable e
        (fs/delete-tree tmp)
        (throw e)))))

(defn- print-result! [{:keys [check ok? detail]}]
  (println (format "  [%s] %-12s %s" (if ok? "PASS" "FAIL") (name check) detail))
  (flush))

(defn- run-check! [enabled descriptor k thunk]
  (when (and (enabled k) (get-in descriptor [:checks k]))
    (let [result (thunk)]
      (print-result! result)
      result)))

(defn- run-checks!
  "Run each enabled check sequentially and print its result as soon as it
   completes. Returns the vector of result maps."
  [root descriptor combo-edn enabled]
  (let [{:keys [denylist ns-prefix-exempt commands]} descriptor
        underscore (str/replace (:name combo-edn) "-" "_")
        thunks     [[:no-cruft    #(checks/cruft-check root denylist)]
                    [:combo       #(checks/combo-check root combo-edn)]
                    [:residue     #(checks/residue-check root)]
                    [:ns-hyphen   #(checks/ns-hyphen-check root underscore ns-prefix-exempt)]
                    [:lint        #(checks/tool-check :lint root (:lint commands) (:lint-config commands) "clj-kondo")]
                    [:fmt         #(checks/tool-check :fmt root (:fmt commands) (:fmt-config commands) "cljfmt")]
                    [:clj-clean   #(checks/clj-clean-check root (:clj-spec commands))]
                    [:cljs-run    #(checks/cljs-check root (:cljs-once commands))]
                    [:server-boot #(checks/server-boot-check root commands)]]]
    (into [] (keep (fn [[k thunk]] (run-check! enabled descriptor k thunk))) thunks)))

(defn verify-combo
  [{:keys [template combo tier cli-cp keep-tmp verbose] :as opts}]
  (let [descriptor (read-edn (descriptor-path template))
        combo-edn  (read-edn (combo-path template combo))
        tier-kw    (or (some-> tier keyword) (get-in descriptor [:combos (keyword combo) :tier]) :light)
        enabled    (tier-checks tier-kw)]
    (println (str "\n=== " combo " (" (name tier-kw) " tier) ==="))
    (flush)
    (let [{:keys [tmp scaffold]} (scaffold! {:cli-cp cli-cp :template template
                                             :descriptor descriptor :combo-edn combo-edn
                                             :verbose verbose})]
      (try
        (every? :ok? (run-checks! scaffold descriptor combo-edn enabled))
        (finally
          (when-not keep-tmp (fs/delete-tree tmp)))))))

(def ^:private opts-spec
  [[nil "--template ID"  "Template id" :default "full-stack-reagent"]
   [nil "--combo NAME"   "Combo name"]
   [nil "--tier TIER"    "full or light (default: combo's declared tier)"]
   [nil "--cli-cp PATH"  "CLI source classpath dir" :default "../cli/src"]
   [nil "--keep-tmp"     "Keep scaffold temp dir"]
   [nil "--verbose"      "Show CLI scaffold stdout (default: hide; shown only on failure)"]])

(defn -main [& argv]
  (let [{:keys [options errors]} (cli/parse-opts argv opts-spec)]
    (when errors (println "args error:" errors) (System/exit 2))
    (when-not (:combo options) (println "missing --combo") (System/exit 2))
    (let [ok? (verify-combo options)]
      (System/exit (if ok? 0 1)))))

(defn run-all [argv]
  (let [{:keys [options]} (cli/parse-opts argv opts-spec)
        template (:template options)
        descriptor (read-edn (descriptor-path template))
        results (mapv (fn [[combo {:keys [tier]}]]
                        (verify-combo (assoc options :combo (name combo) :tier (name tier))))
                      (:combos descriptor))]
    (System/exit (if (every? true? results) 0 1))))
