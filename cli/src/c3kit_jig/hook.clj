(ns c3kit-jig.hook
  (:refer-clojure :exclude [run!])
  (:require [babashka.fs :as fs]
            [babashka.process :as p]))

(defn run!
  "Shell out to `<stage-dir>/c3kit-template.bb <stage-dir>` if the hook file exists.
   Non-zero exit throws ex-info with {:features? true :reason :hook} so main maps
   it to exit code 8."
  [stage-dir]
  (let [hook (fs/path stage-dir "c3kit-template.bb")]
    (when (fs/exists? hook)
      (let [res (p/shell {:dir (str stage-dir) :continue true}
                         "bb" (str hook) (str stage-dir))]
        (when-not (zero? (:exit res))
          (throw (ex-info (str "template hook failed: exit " (:exit res))
                          {:features? true :reason :hook
                           :exit (:exit res)
                           :err  (:err res)})))))))
