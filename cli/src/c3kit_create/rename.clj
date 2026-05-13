(ns c3kit-create.rename
  (:require [clojure.string :as str]))

(def ^:private NAME-RE #"^[a-z][a-z0-9]*(-[a-z0-9]+)*$")

(def ^:private RESERVED
  #{"clojure" "clojurescript" "java" "javascript" "cljs" "cljc" "test" "specs"})

(defn variants
  "Given a kebab-case name, return the four spelling variants."
  [kebab]
  (let [parts (str/split kebab #"-")]
    {:hyphen     kebab
     :underscore (str/join "_" parts)
     :pascal     (apply str (map str/capitalize parts))
     :upper      (str/upper-case (str/join "_" parts))}))

(defn- pascal-of [s]
  (apply str (map str/capitalize (str/split s #"-"))))

(defn- upper-prefix-of [s]
  (let [trimmed (str/replace s #"_+$" "")]
    (str (str/upper-case (str/replace trimmed #"-" "_")) "_")))

(defn replace-token
  "Rewrite every variant of source-token in `s` per the flag map, using user variants."
  [s source-token flags user]
  (let [src-hyphen     source-token
        src-snake      (str/replace source-token #"-" "_")
        src-pascal     (pascal-of source-token)
        src-upper-pfx  (upper-prefix-of source-token)
        user-upper-pfx (str (:upper user) "_")
        ;; Order: most-specific first to avoid clobbering.
        replacements
        (cond-> []
          (:upper-prefix flags) (conj [src-upper-pfx user-upper-pfx])
          (:pascal flags)       (conj [src-pascal     (:pascal user)])
          (:underscore flags)   (conj [src-snake      (:underscore user)])
          (:hyphen flags)       (conj [src-hyphen     (:hyphen user)]))]
    (reduce (fn [acc [from to]] (str/replace acc from to))
            s replacements)))

(defn replace-many
  "Apply all tokens to `s`, longest source-token first to disambiguate overlapping."
  [s tokens user]
  (let [sorted (sort-by #(- (count (first %))) tokens)]
    (reduce (fn [acc [src flags]] (replace-token acc src flags user))
            s sorted)))

(defn reserved? [name] (boolean (RESERVED name)))

(defn validate-name
  "Throws ex-info if `nm` is invalid; otherwise returns `nm`."
  [nm tokens]
  (cond
    (or (not (string? nm)) (not (re-matches NAME-RE nm)))
    (throw (ex-info (str "invalid project name: " (pr-str nm))
                    {:name? true :reason :regex}))

    (reserved? nm)
    (throw (ex-info (str "name is reserved: " nm)
                    {:name? true :reason :reserved}))

    (some #(= nm %) (keys tokens))
    (throw (ex-info (str "name collides with template source token: " nm)
                    {:name? true :reason :token-collision}))

    :else nm))
