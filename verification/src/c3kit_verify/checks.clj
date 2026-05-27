(ns c3kit-verify.checks
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

;; --- Pure decision cores ---

(def ^:private NS-RE #"\(ns\s+([A-Za-z0-9_.*+!?<>=$-]+)")

(defn ns-prefix-violation
  "Return the offending ns symbol string if the file's ns form uses the
   underscore project prefix, else nil. `underscore` is the snake_case project
   name (e.g. \"my_app\"). A single-word project (hyphen == underscore) never
   violates."
  [content underscore]
  (when-let [sym (second (re-find NS-RE content))]
    (when (or (= sym underscore) (str/starts-with? sym (str underscore ".")))
      sym)))

(def ^:private LOG-LINE-RE
  #"(\d{4}-\d{2}-\d{2})|(\d{2}:\d{2}:\d{2})|\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\b")

(defn clean-spec-output?
  "Pass iff no line looks like a log line (timestamp or level marker).
   speclj progress/summary lines contain neither, so they pass."
  [s]
  (let [offending (->> (str/split-lines (or s ""))
                       (filter #(re-find LOG-LINE-RE %))
                       vec)]
    {:ok? (empty? offending) :offending offending}))

(defn tool-result
  "Decide pass/fail for a config-driven external tool (clj-kondo, cljfmt).
   Missing config is a failure; otherwise pass iff exit 0."
  [{:keys [config-exists? exit tool config]}]
  (cond
    (not config-exists?) {:ok? false :detail (str "no " config " shipped")}
    (zero? exit) {:ok? true :detail (str tool " clean")}
    :else {:ok? false :detail (str tool " reported findings (exit " exit ")")}))

(defn parse-cljs-result
  "Parse a speclj-style 'N examples, M failures' summary. Pass iff exit 0,
   examples>0, failures=0."
  [out exit]
  (let [m (re-find #"(\d+)\s+examples?,\s+(\d+)\s+failures?" (or out ""))
        examples (some-> m (nth 1) parse-long)
        failures (some-> m (nth 2) parse-long)]
    {:ok? (and (zero? exit) (some? examples) (pos? examples) (= 0 failures))
     :examples examples
     :failures failures}))

;; --- Effectful check shells ---

(defn- rel [root ^java.io.File f]
  (str (.relativize (.toPath (fs/file root)) (.toPath f))))

(defn- clj-files [root]
  (->> (file-seq (fs/file root))
       (filter #(.isFile %))
       (filter #(re-find #"\.(clj|cljc|cljs)$" (.getName %)))))

(defn cruft-check
  "Fail if any glob in `globs` matches a path inside the scaffold."
  [root globs]
  (let [hits (mapcat (fn [g] (map str (fs/glob root g))) globs)
        hits (sort (distinct (map #(rel root (fs/file %)) hits)))]
    {:check :no-cruft
     :ok?   (empty? hits)
     :detail (if (empty? hits) "no cruft" (str "cruft present: " (str/join ", " hits)))}))

(defn ns-hyphen-check
  "Fail if any clj/cljc/cljs file's ns form uses the underscore project prefix.
   Files whose relative path is in `exempt` are skipped."
  [root underscore exempt]
  (let [exempt (set exempt)
        viols  (for [^java.io.File f (clj-files root)
                     :let [r (rel root f)]
                     :when (not (exempt r))
                     :let [sym (ns-prefix-violation (slurp f) underscore)]
                     :when sym]
                 (str r " -> " sym))]
    {:check :ns-hyphen
     :ok?   (empty? viols)
     :detail (if (empty? viols) "ns forms hyphenated" (str "underscore ns forms: " (str/join ", " viols)))}))

(defn residue-check
  "Fail if any @c3kit/feature or @c3kit/db marker survived in the scaffold."
  [root]
  (let [{:keys [out exit]} (p/sh {:continue true} "grep" "-rEl" "@c3kit/(feature|db)" (str root))
        hits (->> (str/split-lines (or out "")) (remove str/blank?) (map #(rel root (fs/file %))) sort)]
    (cond
      (>= exit 2) {:check :residue :ok? false :detail (str "grep error (exit " exit ")")}
      (seq hits)  {:check :residue :ok? false :detail (str "residual markers in: " (str/join ", " hits))}
      :else       {:check :residue :ok? true :detail "no residue"})))

(defn combo-check
  "Port of verify-scaffold's structural assertions for one combo edn."
  [root {:keys [must-exist must-not-exist file-contains file-not-contains]}]
  (let [errs (atom [])
        full (fn [p] (str (fs/path root p)))]
    (doseq [p must-exist]
      (when-not (fs/exists? (full p)) (swap! errs conj (str "must-exist missing: " p))))
    (doseq [p must-not-exist]
      (when (fs/exists? (full p)) (swap! errs conj (str "must-not-exist present: " p))))
    (doseq [[p strs] file-contains s strs]
      (if-not (fs/exists? (full p))
        (swap! errs conj (str "file-contains: missing file " p))
        (when-not (str/includes? (slurp (full p)) s)
          (swap! errs conj (str "file-contains miss: " p " <- " (pr-str s))))))
    (doseq [[p strs] file-not-contains s strs]
      (when (and (fs/exists? (full p)) (str/includes? (slurp (full p)) s))
        (swap! errs conj (str "file-not-contains hit: " p " -> " (pr-str s)))))
    {:check :combo
     :ok?   (empty? @errs)
     :detail (if (empty? @errs) "combo ok" (str/join "; " @errs))}))

;; --- Spec / lint / fmt / server checks ---
;; Each has a pure `*`-core (takes a result map) and an effectful wrapper
;; (runs the process). Tests target the pure cores.

(defn clj-clean-check* [{:keys [exit out]}]
  (let [clean (clean-spec-output? out)]
    {:check :clj-clean
     :ok?   (and (zero? exit) (:ok? clean))
     :detail (cond
               (not (zero? exit)) (str "clj specs exit " exit)
               (:ok? clean)       "clj specs clean"
               :else              (str "log noise: " (str/join " | " (take 3 (:offending clean)))))}))

(defn clj-clean-check [root cmd]
  (let [{:keys [exit out err]} (apply p/sh {:dir root :continue true} cmd)]
    (clj-clean-check* {:exit exit :out (str out "\n" err)})))

(defn tool-check* [check result]
  (assoc (tool-result result) :check check))

(defn tool-check
  "check  — :lint or :fmt
   cmd    — command vector run inside the scaffold
   config — relative path that must exist (e.g. \".clj-kondo/config.edn\")
   tool   — display name"
  [check root cmd config tool]
  (let [config-exists? (fs/exists? (fs/path root config))
        {:keys [exit]} (if config-exists?
                         (apply p/sh {:dir root :continue true} cmd)
                         {:exit 0})]
    (tool-check* check {:config-exists? config-exists? :exit exit :tool tool :config config})))

(defn cljs-check* [{:keys [exit out]}]
  (assoc (parse-cljs-result out exit) :check :cljs-run))

(defn cljs-check [root cmd]
  (let [{:keys [exit out err]} (apply p/sh {:dir root :continue true} cmd)]
    (cljs-check* {:exit exit :out (str out "\n" err)})))

(defn server-boot-check
  "Run migrate, start the server in the background, poll its port for any HTTP
   response, then kill it. Pass iff a response arrives before timeout."
  [root {:keys [migrate run port]}]
  (apply p/sh {:dir root :continue true} migrate)
  (let [proc (apply p/process {:dir root :extra-env {"PORT" (str port)}} run)
        url  (str "http://localhost:" port "/")
        deadline (+ (System/currentTimeMillis) 60000)]
    (try
      (loop []
        (let [resp (try (-> (p/sh {:continue true} "curl" "-s" "-o" "/dev/null" "-w" "%{http_code}" url) :out)
                        (catch Exception _ nil))]
          (cond
            (and resp (re-matches #"[1-5]\d\d" (str/trim (or resp "")))) {:check :server-boot :ok? true :detail (str "HTTP " (str/trim resp))}
            (> (System/currentTimeMillis) deadline) {:check :server-boot :ok? false :detail "server did not respond within 60s"}
            :else (do (Thread/sleep 1000) (recur)))))
      (finally (p/destroy-tree proc)))))
