(ns c3kit-jig.main
  (:require [babashka.fs :as fs]
            [c3kit-jig.args :as args]
            [c3kit-jig.fetch :as fetch]
            [c3kit-jig.fs :as cfs]
            [c3kit-jig.manifest :as manifest]
            [c3kit-jig.postscaffold :as ps]
            [c3kit-jig.render :as render]
            [c3kit-jig.rename :as rn]
            [c3kit-jig.ui :as ui]
            [c3kit-jig.version :as v]
            [c3kit-jig.wizard :as wizard])
  (:gen-class))

(def REPO-URL "https://github.com/cleancoders/c3kit-jig")
(def DEFAULT-REF "main")

(defn exit [code] (System/exit code))

(defn- resolve-templates-dir [opts]
  (or (:template-dir opts)
      (System/getenv "C3KIT_TEMPLATES")))

;; NOTE on requiring-resolve: bb's sci analyzer on Linux (1.12.218) fails to
;; resolve `fetch/clone-repo!` and `fetch/list-local-templates` at analysis time
;; even though the require above is correct and the symbols exist. macOS-bb
;; resolves them fine. Using requiring-resolve defers symbol lookup to runtime,
;; sidestepping the analysis-phase failure.

(defn- ensure-template-dir
  "Return opts with `:template-dir` set. If already set, no-op. Otherwise clones
   the upstream repo into a tmp stage and sets `:template-dir` to its templates/
   subdir. Caller must clean up via ::clone-stage."
  [opts]
  (if (resolve-templates-dir opts)
    opts
    (let [stage (cfs/stage-dir)
          tdir  ((requiring-resolve 'c3kit-jig.fetch/clone-repo!)
                 REPO-URL (or (:template-ref opts) DEFAULT-REF) stage)]
      (assoc opts :template-dir tdir ::clone-stage stage))))

(defn- prompt-template [opts]
  (let [opts'   (ensure-template-dir opts)
        tdir    (resolve-templates-dir opts')
        choices ((requiring-resolve 'c3kit-jig.fetch/list-local-templates) tdir)]
    (when-not (seq choices)
      (throw (ex-info (str "no templates available in " tdir) {:fetch? true})))
    (let [menu (mapv (fn [c] {:id (:id c) :label (:label c)}) choices)
          chosen (wizard/prompt-select "Template" menu (-> menu first :id))]
      [chosen opts'])))

(defn- prompt-missing
  "Interactively prompt for :name and :template when missing. Skipped under --yes."
  [opts]
  (let [opts (if (:name opts)
               opts
               (assoc opts :name (wizard/prompt-text "Project name" "my-app" identity)))]
    (if (:template opts)
      opts
      (let [[tid opts'] (prompt-template opts)]
        (assoc opts' :template tid)))))

(defn- effective-features
  "Manifest defaults overlaid with CLI --feature overrides."
  [manifest cli-feature]
  (let [defaults (into {} (for [{:keys [id default]} (:features manifest)]
                            [id default]))]
    (merge defaults (or cli-feature {}))))

(defn- effective-db
  "CLI --db wins over manifest :db.default. Validated against :db.options."
  [manifest cli-db]
  (when-let [db (:db manifest)]
    (let [chosen (or cli-db (:default db))
          ids    (set (map :id (:options db)))]
      (when-not (ids chosen)
        (throw (ex-info (str "--db " chosen " not in manifest :db.options "
                             (vec ids))
                        {:name? true :reason :db-choice})))
      {:db chosen})))

(defn- compute-features [manifest cli-feature yes?]
  (if yes?
    (effective-features manifest cli-feature)
    (wizard/prompt-features-checkbox (:features manifest) cli-feature)))

(defn- compute-db [manifest cli-db yes?]
  (cond
    (or yes? cli-db)   (effective-db manifest cli-db)
    (:db manifest)     {:db (wizard/prompt-db (:db manifest) nil)}
    :else              nil))

(def ^:private ERROR-EXIT-CODE
  {:manifest?     6
   :collision?    3
   :fetch?        7
   :features?     8
   :name?         4
   :postscaffold? 9})

(defn- exit-code-for [ex-data]
  (some (fn [[k code]] (when (get ex-data k) code)) ERROR-EXIT-CODE))

(defn- fetch-template! [opts template stage tdir]
  (ui/step "fetching template …")
  (if-let [local (resolve-templates-dir opts)]
    (fetch/from-local-dir local template (str tdir))
    (fetch/from-git REPO-URL (or (:template-ref opts) DEFAULT-REF) template stage (str tdir))))

(defn- target-path [opts nm]
  (str (fs/path (or (:target-parent opts) (fs/cwd)) nm)))

(defn- die-if-target-exists! [target stage]
  (when (fs/exists? target)
    (ui/fail (str "target already exists: " target))
    (cfs/cleanup! stage)
    (exit 3)))

(defn- render-into-stage! [tdir stage manifest nm features db]
  (ui/step "rendering tokens …")
  (let [scaffold (str (fs/path stage "scaffold"))]
    (fs/copy-tree (str tdir) scaffold)
    (render/render! scaffold manifest nm features db (v/current))
    scaffold))

(defn- finalize! [scaffold target opts]
  (ui/step "moving into place …")
  (cfs/move-into-place! scaffold target)
  (when (:git? opts)
    (ui/step "git init + initial commit …")
    (ps/git-init! target))
  (ps/install! target opts))

(defn- handle-scaffold-failure! [e opts]
  (ui/fail (.getMessage e))
  (let [code (exit-code-for (ex-data e))]
    (when (and (nil? code) (:debug opts)) (.printStackTrace e))
    (exit (or code 1))))

(defn- scaffold! [{:keys [name template yes] :as opts}]
  (let [stage (cfs/stage-dir)
        tdir  (fs/path stage template)]
    (try
      (fetch-template! opts template stage tdir)
      (let [manifest (manifest/read-manifest (str tdir))
            nm       (rn/validate-name (or name "my-app") (:tokens manifest))
            target   (target-path opts nm)]
        (die-if-target-exists! target stage)
        (let [features (compute-features manifest (:feature opts) yes)
              db       (compute-db manifest (:db opts) yes)
              scaffold (render-into-stage! tdir stage manifest nm features db)]
          (finalize! scaffold target opts)
          (ui/ok (str "Created " nm))
          (ui/next-steps (:next-steps manifest) nm)))
      (catch Exception e
        (handle-scaffold-failure! e opts))
      (finally
        (cfs/cleanup! stage)))))

(defn -main [& argv]
  (let [{:keys [action options error]} (args/parse argv)]
    (binding [ui/*color?* (ui/tty?)]
      (case action
        :version  (do (println (v/current)) (exit 0))
        :help     (do (println (args/help)) (exit 0))
        :error    (do (ui/fail error) (println (args/help)) (exit 2))
        :list     (do (ui/info "List of templates not yet implemented.") (exit 0))
        :upgrade  (try
                    (let [bin (System/getProperty "babashka.file")
                          r   (v/check-and-download! bin)]
                      (case r
                        :up-to-date (ui/info "already on latest")
                        :upgraded   (ui/ok "upgraded — re-run your command"))
                      (exit 0))
                    (catch Exception e
                      (ui/fail (.getMessage e))
                      (exit 11)))
        :scaffold (cond
                    (and (:yes options) (not (:template options)))
                    (do (ui/fail "Missing required option: --template ID (required with --yes)")
                        (exit 2))
                    :else
                    (let [opts (prompt-missing options)]
                      (try
                        (scaffold! opts)
                        (exit 0)
                        (finally
                          (cfs/cleanup! (::clone-stage opts))))))))))

