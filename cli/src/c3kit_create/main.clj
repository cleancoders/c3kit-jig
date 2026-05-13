(ns c3kit-create.main
  (:require [babashka.fs :as fs]
            [c3kit-create.args :as args]
            [c3kit-create.fetch :as fetch]
            [c3kit-create.fs :as cfs]
            [c3kit-create.manifest :as manifest]
            [c3kit-create.postscaffold :as ps]
            [c3kit-create.render :as render]
            [c3kit-create.rename :as rn]
            [c3kit-create.ui :as ui]
            [c3kit-create.version :as v])
  (:gen-class))

(def REPO-URL "https://github.com/cleancoders/c3kit-starter")
(def DEFAULT-REF "main")

(defn exit [code] (System/exit code))

(defn- resolve-templates-dir [opts]
  (or (:template-dir opts)
      (System/getenv "C3KIT_TEMPLATES")))

(defn- scaffold! [{:keys [name template yes template-ref] :as opts}]
  (let [stage (cfs/stage-dir)
        ref   (or template-ref DEFAULT-REF)
        local (resolve-templates-dir opts)
        tdir  (fs/path stage template)]
    (try
      ;; stage-1: fetch
      (ui/step "fetching template …")
      (if local
        (fetch/from-local-dir local template (str tdir))
        (fetch/from-git REPO-URL ref template stage (str tdir)))

      ;; manifest
      (let [m (manifest/read-manifest (str tdir))
            nm (rn/validate-name (or name "my-app") (:tokens m))
            target (str (fs/path (or (:target-parent opts) (fs/cwd)) nm))]
        (when (fs/exists? target)
          (ui/fail (str "target already exists: " target))
          (cfs/cleanup! stage)
          (exit 3))

        ;; collect features + db (use defaults under --yes)
        (let [features (into {} (for [{:keys [id default]} (:features m)]
                                  [id default]))
              db       (when (:db m) {:db (:default (:db m))})]
          (when-not yes (ui/info "Using defaults (interactive prompts WIP in v0.2)"))

          ;; stage-2: render
          (ui/step "rendering tokens …")
          ;; copy tdir → scaffold and render in place
          (let [scaffold (str (fs/path stage "scaffold"))]
            (fs/copy-tree (str tdir) scaffold)
            (render/render! scaffold
                            (slurp (fs/file (fs/path tdir "c3kit-template.edn")))
                            nm features db)

            ;; stage-4: move
            (ui/step "moving into place …")
            (cfs/move-into-place! scaffold target)

            ;; stage-5: post-scaffold
            (when (:git? opts)
              (ui/step "git init + initial commit …")
              (ps/git-init! target))
            (ps/install! target opts)

            (ui/ok (str "Created " nm)))))
      (catch Exception e
        (cfs/cleanup! stage)
        (let [data (ex-data e)]
          (ui/fail (.getMessage e))
          (cond
            (:manifest? data)     (exit 6)
            (:collision? data)    (exit 3)
            (:fetch? data)        (exit 7)
            (:features? data)     (exit 8)
            (:name? data)         (exit 4)
            (:postscaffold? data) (exit 9)
            :else                 (do (when (:debug opts) (.printStackTrace e))
                                      (exit 1)))))
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
        :scaffold (do (scaffold! options) (exit 0))))))

