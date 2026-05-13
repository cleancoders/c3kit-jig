(ns c3kit-create.manifest
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private ID-RE  #"^[a-z][a-z0-9]*(-[a-z0-9]+)*$")
(def ^:private SEMVER #"^\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?$")

(defn- die [msg] (throw (ex-info msg {:manifest? true})))

(defn- semver? [s] (boolean (and (string? s) (re-matches SEMVER s))))

(defn- check-id [m dir-name]
  (let [id (:id m)]
    (when-not (keyword? id) (die "manifest :id must be a keyword"))
    (when-not (re-matches ID-RE (name id)) (die ":id does not match name regex"))
    (when-not (= (name id) dir-name) (die (str ":id " id " does not equal dir " dir-name)))))

(defn- check-version [m]
  (when-not (semver? (:version m))  (die ":version is not valid semver"))
  (when-not (semver? (:min-cli m))  (die ":min-cli is not valid semver")))

(defn- check-tokens [m]
  (let [tokens (:tokens m)]
    (when-not (map? tokens) (die ":tokens must be a map"))
    (doseq [[k v] tokens]
      (when (or (not (string? k)) (str/blank? k)) (die ":tokens key must be non-blank string"))
      (when-not (map? v) (die ":tokens entry must be a flag map")))))

(defn- check-secrets [m]
  (let [secrets (:secrets m)
        placeholders (map :placeholder secrets)]
    (when-not (sequential? secrets) (die ":secrets must be sequential"))
    (when (not= (count placeholders) (count (distinct placeholders)))
      (die "duplicate :secrets placeholder"))))

(defn- check-features [m]
  (let [feats (:features m)
        ids   (map :id feats)]
    (when-not (sequential? feats) (die ":features must be sequential"))
    (when (not= (count ids) (count (distinct ids)))
      (die "duplicate :features :id"))
    (doseq [{:keys [delete-when-off]} feats
            p delete-when-off]
      (when (or (str/starts-with? p "../") (str/includes? p "/.."))
        (die (str ":delete-when-off escapes template root: " p))))))

(defn- check-db [m]
  (when-let [db (:db m)]
    (let [ids (map :id (:options db))]
      (when-not (seq ids) (die ":db.options must be non-empty"))
      (when (not= (count ids) (count (distinct ids)))
        (die "duplicate :db.options :id"))
      (when-not (some #{(:default db)} ids)
        (die ":db.default not in :db.options")))))

(defn- check-next-steps [m]
  (doseq [step (:next-steps m)]
    (when-not (and (map? step) (string? (:cmd step)))
      (die ":next-steps entry must be {:cmd \"...\"}"))))

(defn validate
  "Validate a parsed manifest. Returns the manifest unchanged on success,
  throws ex-info with {:manifest? true} on failure."
  [m dir-name]
  (check-id m dir-name)
  (check-version m)
  (check-tokens m)
  (check-secrets m)
  (check-features m)
  (check-db m)
  (check-next-steps m)
  m)

(defn read-manifest
  "Read templates/<dir-name>/c3kit-template.edn from the given template dir
   and validate it. Throws ex-info on any failure."
  [^String template-dir]
  (let [path (fs/path template-dir "c3kit-template.edn")
        dir-name (fs/file-name template-dir)]
    (when-not (fs/exists? path)
      (die (str "manifest not found: " path)))
    (let [m (try (edn/read-string (slurp (fs/file path)))
                 (catch Exception e
                   (throw (ex-info "manifest is not valid EDN"
                                   {:manifest? true} e))))]
      (validate m dir-name))))
