(ns c3kit-verify.coupling
  "Static cross-feature coupling scanner for a template directory.

  Goal: catch the class of bug where a file that survives feature X being
  off has an unguarded require of (or fully-qualified reference to) a
  namespace owned by feature Y, so the scaffold breaks the moment a user
  picks `:X on :Y off`.

  All decisions are derived from the template's `c3kit-template.edn`
  manifest plus the static structure of files on disk. No scaffold is
  produced — the scanner reads the template in place."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private NS-RE #"\(ns\s+([A-Za-z0-9_.*+!?<>=$-]+)")

(def ^:private LINE-EQ-RE
  #"^\s*;;\s*@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*=\s*(.*)$")

(def ^:private BLOCK-OPEN-RE
  #"^\s*;;\s*@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*\{")

(def ^:private BLOCK-CLOSE-RE
  #"^\s*;;\s*@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*\}")

;; --- Pure decision cores -------------------------------------------------

(defn extract-ns [content]
  (second (re-find NS-RE (or content ""))))

(defn line-eq-feature
  "Return the feature kw for a `;; @c3kit/feature :X = …` line, else nil."
  [line]
  (when-let [[_ _inv id] (re-find LINE-EQ-RE (or line ""))]
    (keyword id)))

(defn- block-open [line]
  (when-let [[_ _inv id] (re-find BLOCK-OPEN-RE line)]
    {:kind :feature :id (keyword id)}))

(defn- block-close [line]
  (when-let [[_ _inv id] (re-find BLOCK-CLOSE-RE line)]
    {:kind :feature :id (keyword id)}))

(defn update-stack
  "Marker stack tracker. Push on open, pop on close, no-op otherwise."
  [stack line]
  (cond
    (block-open line)  (conj (vec stack) (block-open line))
    (block-close line) (vec (butlast stack))
    :else              (vec stack)))

(defn extract-acme-refs
  "Return every distinct namespace token in `line` that starts with
   `<ns-token>.` — covers both `[ns-token.foo.bar :as x]` requires and
   `ns-token.foo.bar/sym` fully-qualified references. Ordering preserves
   first appearance."
  [line ns-token]
  (let [re (re-pattern (str "\\b" (java.util.regex.Pattern/quote ns-token)
                            "\\.[A-Za-z][A-Za-z0-9._-]*"))
        hits (->> (re-seq re (or line ""))
                  (map #(let [s %] (if (str/ends-with? s "/") (subs s 0 (dec (count s))) s)))
                  (map #(first (str/split % #"/")))
                  distinct
                  vec)]
    hits))

(defn- extras-paths [manifest]
  (into {} (for [feat (:features manifest)
                 p    (or (:extras feat) [])]
             [p (:id feat)])))

(defn- path-segs [relpath]
  (vec (remove str/blank? (str/split (str relpath) #"/"))))

(defn- feature-dir-match? [segs ns-token feature-name]
  (loop [s segs]
    (cond
      (< (count s) 2)                          false
      (and (= (first s) ns-token)
           (= (second s) feature-name))        true
      :else                                    (recur (rest s)))))

(defn- feature-file-match? [segs ns-token feature-name]
  (and (>= (count segs) 2)
       (= (nth segs (- (count segs) 2)) ns-token)
       (some #(#{(str feature-name "." %)
                 (str feature-name "_spec." %)} (last segs))
             ["clj" "cljc" "cljs"])))

(defn- extras-match [relpath extras]
  (some (fn [[pat feat-id]]
          (let [trimmed (str/replace pat #"/$" "")]
            (when (or (= relpath pat)
                      (= relpath trimmed)
                      (str/starts-with? relpath (str trimmed "/")))
              feat-id)))
        extras))

(defn file-owner
  "Return the feature kw that owns `relpath`, else `:always`."
  [relpath manifest]
  (let [ns-token (or (:namespace-token manifest) "acme")
        segs     (path-segs relpath)
        extras   (extras-paths manifest)
        feat-ids (map :id (:features manifest))]
    (or (extras-match relpath extras)
        (some (fn [id]
                (let [fname (name id)]
                  (when (or (feature-dir-match? segs ns-token fname)
                            (feature-file-match? segs ns-token fname))
                    id)))
              feat-ids)
        :always)))

;; --- Per-file scan -------------------------------------------------------

(defn file-couplings
  "Return a vector of coupling violations for one file.

   file-info — {:relpath str :owner kw :content str}
   ns->owner — {ns-string → feature-kw-or-:always}
   ns-token  — \"acme\""
  [{:keys [relpath owner content]} ns->owner ns-token]
  (loop [in     (str/split-lines (or content ""))
         stack  []
         line-no 1
         acc    []]
    (if (empty? in)
      acc
      (let [line     (first in)
            line-eq  (line-eq-feature line)
            stack'   (update-stack stack line)
            on-block? (boolean (block-open line))
            on-close? (boolean (block-close line))
            ;; Inside-block scope = the stack AFTER applying open/close
            ;; for THIS line. Refs on the same line as a marker are
            ;; considered guarded by that marker.
            guards   (into #{} (map :id) stack')
            guards   (cond-> guards line-eq (conj line-eq))
            refs     (when-not (or on-block? on-close?)
                       (extract-acme-refs line ns-token))
            viols    (for [ref refs
                           :let [target-owner (get ns->owner ref :always)]
                           :when (and (not= target-owner :always)
                                      (not= target-owner owner)
                                      (not (guards target-owner)))]
                       {:file     relpath
                        :line     line-no
                        :ref      ref
                        :ref-owner target-owner
                        :scope-owner owner})]
        (recur (rest in) stack' (inc line-no) (into acc viols))))))

;; --- Effectful template scan --------------------------------------------

(defn- source-files
  "Return seq of [relpath ^File] for clj/cljc/cljs files under common
   source roots inside the template."
  [template-root]
  (let [roots ["src" "spec" "dev"]
        re    #"\.(clj|cljc|cljs)$"]
    (for [root roots
          :let [dir (fs/file (fs/path template-root root))]
          :when (and (fs/exists? dir) (fs/directory? dir))
          ^java.io.File f (file-seq dir)
          :when (and (.isFile f) (re-find re (.getName f)))]
      [(str (.relativize (.toPath (fs/file template-root)) (.toPath f))) f])))

(defn- build-ns-index
  "Map every ns symbol declared in the template to its owner feature."
  [_template-root manifest files]
  (into {} (for [[relpath f] files
                 :let [content (slurp f)
                       ns-sym  (extract-ns content)]
                 :when ns-sym]
             [ns-sym (file-owner relpath manifest)])))

(defn scan-template
  "Run the static coupling scan. Returns
   {:violations [{:file :line :ref :ref-owner :scope-owner} …]}.

   When no violations, :violations is empty."
  [template-root]
  (let [manifest (edn/read-string (slurp (fs/file (fs/path template-root "c3kit-template.edn"))))
        ns-token (or (:namespace-token manifest) "acme")
        files    (vec (source-files template-root))
        ns->own  (build-ns-index template-root manifest files)
        viols    (vec (mapcat (fn [[relpath f]]
                                (let [content (slurp f)]
                                  (file-couplings {:relpath relpath
                                                   :owner   (file-owner relpath manifest)
                                                   :content content}
                                                  ns->own ns-token)))
                              files))]
    {:violations viols :ns-index ns->own}))

(defn coupling-check
  "Harness check entry point. Pass when scan finds no violations."
  [template-root]
  (let [{:keys [violations]} (scan-template template-root)]
    {:check :feature-coupling
     :ok?   (empty? violations)
     :detail (if (empty? violations)
               "no cross-feature couplings"
               (str (count violations) " coupling(s): "
                    (str/join "; "
                              (for [v (take 5 violations)]
                                (str (:file v) ":" (:line v)
                                     " " (name (:scope-owner v))
                                     " → " (:ref v)
                                     " [" (name (:ref-owner v)) "]")))
                    (when (> (count violations) 5) " …")))}))
