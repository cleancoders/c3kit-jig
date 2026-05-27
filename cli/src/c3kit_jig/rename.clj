(ns c3kit-jig.rename
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

(def ^:private STRING-LIT-RE #"\"(?:\\.|[^\"\\])*\"")

(defn- hyphenate-token
  "Replace pascal/upper-prefix as usual, but use the HYPHEN user variant for the base token."
  [s source-token flags user]
  (replace-token s source-token (dissoc flags :underscore) user))

(defn- underscore-token
  "Replace pascal/upper-prefix as usual, but use the UNDERSCORE user variant for the base token."
  [s source-token flags user]
  (replace-token s source-token (dissoc flags :hyphen) user))

(defn- ns-arg-string?
  "True if literal `lit` (quotes included) contains the source token immediately
   followed by a dot + symbol char — i.e. a namespace ref like \"acme.main\",
   not a bare path prefix like \"acme\"."
  [lit source-token]
  (boolean (re-find (re-pattern (str (java.util.regex.Pattern/quote source-token) "\\.[A-Za-z]"))
                    lit)))

(defn- split-on-strings
  "Return a vector of [kind text] segments where kind is :code or :str,
   preserving order so (apply str (map second …)) reconstructs the input."
  [content]
  (let [m (re-matcher STRING-LIT-RE content)]
    (loop [acc [] last 0]
      (if (.find m)
        (recur (-> acc
                   (conj [:code (subs content last (.start m))])
                   (conj [:str (.group m)]))
               (.end m))
        (conj acc [:code (subs content last)])))))

(defn- replace-content-one
  "Apply one token to `content` with the segment rule appropriate to `ext`."
  [content [source-token flags] user ext]
  (->> (split-on-strings content)
       (map (fn [[kind text]]
              (cond
                (#{"clj" "cljc" "cljs"} ext)
                (if (= kind :code)
                  (hyphenate-token text source-token flags user)
                  (underscore-token text source-token flags user))

                (= ext "edn")
                (if (and (= kind :str) (ns-arg-string? text source-token))
                  (hyphenate-token text source-token flags user)
                  (underscore-token text source-token flags user))

                :else
                (underscore-token text source-token flags user))))
       (apply str)))

(defn replace-content
  "Context-aware token replacement for file CONTENT. `ext` is the lowercased
   file extension (no dot). For clj/cljc/cljs and edn, distinguishes code/symbol
   context (hyphen) from string/path context (underscore). For any other ext,
   single-variant underscore replacement."
  [content tokens user ext]
  (let [sorted (sort-by #(- (count (first %))) tokens)]
    (if (#{"clj" "cljc" "cljs" "edn"} ext)
      (reduce (fn [acc tok] (replace-content-one acc tok user ext)) content sorted)
      (reduce (fn [acc [src flags]] (underscore-token acc src flags user)) content sorted))))

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
