(ns c3kit-create.main
  (:require [c3kit-create.args :as args]
            [c3kit-create.ui :as ui]
            [c3kit-create.version :as v])
  (:gen-class))

(defn- exit [code] (System/exit code))

(defn -main [& argv]
  (let [{:keys [action options error]} (args/parse argv)]
    (binding [ui/*color?* (ui/tty?)]
      (case action
        :version  (do (println (v/current)) (exit 0))
        :help     (do (println (args/help)) (exit 0))
        :error    (do (ui/fail error)
                      (println (args/help))
                      (exit 2))
        :list     (do (ui/info "List of templates not yet implemented.") (exit 0))
        :upgrade  (do (ui/info "Upgrade not yet implemented.") (exit 0))
        :scaffold (do (ui/info "Scaffold not yet implemented.") (exit 0))))))
