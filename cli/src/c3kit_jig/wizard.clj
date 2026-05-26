(ns c3kit-jig.wizard
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [c3kit-jig.ui :as ui]))

(defn- read-trim []
  (let [line (read-line)]
    (when line (str/trim line))))

(defn- prompt-text-once [label default validate-fn]
  (print (str label
              (when default (str " [" default "]"))
              ": "))
  (flush)
  (let [raw (read-trim)
        val (if (and (str/blank? raw) (some? default)) default raw)]
    (try
      {:ok (validate-fn val)}
      (catch Exception e
        (ui/fail (str "  " (.getMessage e)))
        nil))))

(defn prompt-text
  "Prompt with optional default; call `validate-fn` (which may throw) on input.
   Returns validated value."
  [label default validate-fn]
  (loop []
    (let [result (prompt-text-once label default validate-fn)]
      (if result
        (:ok result)
        (recur)))))

(defn prompt-yn [label default]
  (let [d (if default "[Y/n]" "[y/N]")]
    (loop []
      (print (str label "  " d ": "))
      (flush)
      (let [raw (some-> (read-trim) str/lower-case)]
        (cond
          (str/blank? raw)         default
          (#{"y" "yes"} raw)       true
          (#{"n" "no"}  raw)       false
          :else (do (ui/fail "  please answer y or n") (recur)))))))

(defn gum-available?
  "True when `gum` is on PATH and stdout is a real TTY."
  []
  (and (ui/tty?)
       (try
         (zero? (:exit (p/shell {:out :string :err :string :continue true}
                                "sh" "-c" "command -v gum")))
         (catch Exception _ false))))

(defn gum-choose-one
  "Pipe `labels` (one per line) to `gum choose` with `selected` (string label)
   pre-highlighted. Returns chosen label string. Throws on non-zero."
  [header labels selected]
  (let [res (p/shell {:in (str/join "\n" labels) :out :string :err :inherit :continue true}
                     "gum" "choose"
                     "--header" header
                     "--selected" (or selected ""))]
    (when-not (zero? (:exit res))
      (throw (ex-info "gum exited non-zero" {:exit (:exit res)})))
    (str/trim (:out res))))

(defn- prompt-select-numbered [label options default-id]
  (println (str label ":"))
  (doseq [[i opt] (map-indexed vector options)]
    (println (str "  " (inc i) ") " (:label opt)
                  (when (= (:id opt) default-id) " (default)"))))
  (loop []
    (print "Choice [default]: ") (flush)
    (let [raw (read-trim)]
      (cond
        (str/blank? raw) default-id
        :else
        (let [n   (try (Integer/parseInt raw) (catch Exception _ -1))
              opt (when (<= 1 n (count options)) (nth options (dec n)))]
          (if opt
            (:id opt)
            (do (ui/fail "  invalid choice") (recur))))))))

(defn- options-safe-for-gum? [options]
  (let [labels (map :label options)]
    (= (count labels) (count (set labels)))))

(defn prompt-select [label options default-id]
  (if (and (gum-available?) (options-safe-for-gum? options))
    (try
      (let [labels        (mapv :label options)
            label->id     (zipmap labels (map :id options))
            default-label (some #(when (= (:id %) default-id) (:label %)) options)
            picked        (gum-choose-one (str label ":") labels default-label)]
        (or (label->id picked) default-id))
      (catch Exception _
        (prompt-select-numbered label options default-id)))
    (prompt-select-numbered label options default-id)))

(defn prompt-features
  "Prompt y/n for each feature in `features` whose `:id` is not in `overrides`.
   `overrides` is a map of `{feature-id boolean}` from CLI flags.
   Returns full `{id boolean}` map covering every feature."
  [features overrides]
  (reduce (fn [acc {:keys [id prompt default]}]
            (assoc acc id
                   (if (contains? overrides id)
                     (get overrides id)
                     (prompt-yn (or prompt (str (name id) "?")) default))))
          {}
          (or features [])))

(defn gum-choose
  "Pipe `labels` (one per line) to `gum choose --no-limit` with `selected` (set of
   label strings) pre-checked. Returns set of chosen labels. Throws on non-zero."
  [labels selected]
  (let [in  (str/join "\n" labels)
        sel (str/join "," (filter (set labels) selected))
        res (p/shell {:in in :out :string :err :inherit :continue true}
                     "gum" "choose" "--no-limit"
                     "--header" "Select features (space toggles, enter confirms)"
                     "--selected" sel)]
    (when-not (zero? (:exit res))
      (throw (ex-info "gum exited non-zero" {:exit (:exit res)})))
    (->> (:out res)
         str/split-lines
         (map str/trim)
         (remove str/blank?)
         set)))

(defn- feature-label [{:keys [id prompt]}]
  (or prompt (str (name id) "?")))

(defn- safe-for-gum? [features]
  (let [labels (map feature-label features)]
    (and (every? #(not (str/includes? % ",")) labels)
         (= (count labels) (count (set labels))))))

(defn prompt-features-checkbox
  "Multi-select features via `gum choose --no-limit`. Falls back to `prompt-features`
   yn-loop when gum is unavailable, on cancel/error, or when feature labels can't
   round-trip safely. Respects CLI `overrides` map."
  [features overrides]
  (let [overrides (or overrides {})
        [forced prompted] [(filter #(contains? overrides (:id %)) features)
                           (remove  #(contains? overrides (:id %)) features)]
        forced-map (into {} (map (fn [{:keys [id]}] [id (get overrides id)]) forced))
        prompted-map
        (cond
          (empty? prompted) {}
          (or (not (gum-available?))
              (not (safe-for-gum? prompted)))
          (prompt-features prompted nil)
          :else
          (try
            (let [labels      (mapv feature-label prompted)
                  label->id   (zipmap labels (map :id prompted))
                  preselected (->> prompted (filter :default) (map feature-label))
                  picked      (gum-choose labels preselected)]
              (into {} (for [label labels]
                         [(label->id label) (contains? picked label)])))
            (catch Exception _
              (prompt-features prompted nil))))]
    (merge prompted-map forced-map)))

(defn prompt-db
  "Prompt for database choice from manifest `:db` map. Returns chosen id.
   If `override` is non-nil, returns it without prompting. Returns nil when
   `db` is nil."
  [db override]
  (cond
    (some? override) override
    (nil? db)        nil
    :else            (prompt-select (or (:prompt db) "Database")
                                    (:options db)
                                    (:default db))))
