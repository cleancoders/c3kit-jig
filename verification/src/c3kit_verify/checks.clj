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
