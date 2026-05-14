#!/usr/bin/env bb
;; Isolation test for c3kit-template.bb hook.
;; Builds a minimal fake-scaffold dir, invokes the hook, asserts.

(require '[babashka.fs :as fs])
(require '[babashka.process :as p])
(require '[clojure.string :as str])

(def tmp (str (fs/create-temp-dir {:prefix "hook-test-"})))

;; Fixture: minimal scaffold dir
(fs/create-dirs (str tmp "/bin"))
(fs/create-dirs (str tmp "/src/clj/my_app"))

(spit (str tmp "/bin/db.template.sqlite")
      "#!/usr/bin/env bash\necho 'sqlite ready for acme_dev'\n")
(spit (str tmp "/bin/db.template.memory")
      "#!/usr/bin/env bash\necho 'memory'\n")

(spit (str tmp "/src/clj/my_app/config.clj")
      (str "(def development\n"
           "  (assoc base\n"
           "    :bucket memory-local                           ;; HEAD default; replaced by line below at scaffold\n"
           "    :bucket sqlite-local\n"
           "    :host \"http://localhost:8123\"))\n"))

(spit (str tmp "/deps.edn") "{:paths [\"src\"]}\n")

(spit (str tmp "/.c3kit-create-context.edn")
      (str "{:name \"my-app\"\n"
           " :name-variants {:hyphen \"my-app\" :underscore \"my_app\" :pascal \"MyApp\"}\n"
           " :db :sqlite\n"
           " :features {:content true :ssr true :markdownc true :auth true}\n"
           " :secrets []\n"
           " :template :full-stack-reagent\n"
           " :template-version \"0.1.0\"\n"
           " :cli-version \"0.1.0\"}\n"))

;; Run hook
(let [hook (str (System/getProperty "user.dir") "/c3kit-template.bb")
      result (p/sh "bb" hook tmp)]
  (println "exit:" (:exit result))
  (println "stdout:" (:out result))
  (when (seq (:err result)) (println "stderr:" (:err result)))
  (when-not (zero? (:exit result)) (System/exit (:exit result))))

;; Assertions
(let [bin-db (str tmp "/bin/db")]
  (assert (fs/exists? bin-db) "bin/db should exist")
  (assert (str/includes? (slurp bin-db) "my_app_dev") "bin/db should be token-renamed")
  (assert (not (fs/exists? (str tmp "/bin/db.template.sqlite"))) "sqlite template should be removed")
  (assert (not (fs/exists? (str tmp "/bin/db.template.memory"))) "memory template should be removed"))

(let [config (slurp (str tmp "/src/clj/my_app/config.clj"))]
  (assert (not (str/includes? config "memory-local")) "HEAD-default line should be removed")
  (assert (str/includes? config "sqlite-local") "scaffolded :bucket should remain"))

(fs/delete-tree tmp)
(println "PASS")
