(ns c3kit-create.main
  (:require [c3kit-create.version :as v])
  (:gen-class))

(defn -main [& args]
  (cond
    (some #{"--version"} args)
    (do (println (v/current)) (System/exit 0))

    :else
    (do (println "c3kit-create" (v/current))
        (println "(no subcommand implemented yet)")
        (System/exit 0))))
