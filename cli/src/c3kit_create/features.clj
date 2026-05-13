(ns c3kit-create.features
  (:require [clojure.string :as str]))

;; Markers — substring matching, comment syntax agnostic.

(def ^:private LINE-EQ-RE
  #"@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*=\s*(.*)$")

(def ^:private BLOCK-OPEN-RE
  #"@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*\{")

(def ^:private BLOCK-CLOSE-RE
  #"@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*\}")

(def ^:private DB-OPEN-RE
  #"@c3kit/db\s+:([A-Za-z][A-Za-z0-9-]*)\s*\{")

(def ^:private DB-CLOSE-RE
  #"@c3kit/db\s+:([A-Za-z][A-Za-z0-9-]*)\s*\}")

(defn- die [msg] (throw (ex-info msg {:features? true})))

(defn- feature-on? [features id inverse?]
  (let [raw (get features id)]
    (if inverse? (not raw) (boolean raw))))

(defn- handle-line-eq [line features]
  (when-let [m (re-find LINE-EQ-RE line)]
    (let [[_ inv id-str code] m
          on? (feature-on? features (keyword id-str) (some? inv))]
      (if on? code ::drop))))

(defn- block-open [line]
  (when-let [m (re-find BLOCK-OPEN-RE line)]
    (let [[_ inv id-str] m]
      {:kind :feature :id (keyword id-str) :inverse? (some? inv)})))

(defn- block-close [line]
  (when-let [m (re-find BLOCK-CLOSE-RE line)]
    (let [[_ inv id-str] m]
      {:kind :feature :id (keyword id-str) :inverse? (some? inv)})))

(defn- db-open [line]
  (when-let [m (re-find DB-OPEN-RE line)]
    {:kind :db :id (keyword (second m))}))

(defn- db-close [line]
  (when-let [m (re-find DB-CLOSE-RE line)]
    {:kind :db :id (keyword (second m))}))

(defn strip
  "Strip feature/db markers from text. Returns rewritten string.

   features  — map of feature-id → bool
   db-choice — map with optional :db keyword for db-marker selection"
  [text features db-choice]
  (let [lines (str/split-lines text)]
    (loop [in    lines
           out   []
           stack nil]                   ; list of open block descriptors
      (if (empty? in)
        (cond
          (seq stack) (die (str "unclosed marker: " (first stack)))
          :else       (str/join "\n" out))
        (let [line (first in)
              rest (rest in)]
          ;; A line-eq marker only matches when no block is open.
          (cond
            (and (empty? stack) (re-find LINE-EQ-RE line))
            (let [r (handle-line-eq line features)]
              (recur rest (if (= r ::drop) out (conj out r)) stack))

            (or (block-open line) (db-open line))
            (let [open (or (block-open line) (db-open line))]
              (when (seq stack) (die "nested marker"))
              (recur rest out [open]))

            (or (block-close line) (db-close line))
            (let [close (or (block-close line) (db-close line))
                  [open] stack]
              (when-not open                              (die "close without open"))
              (when-not (= (:kind open) (:kind close))   (die "marker kind mismatch"))
              (when-not (= (:id open)   (:id close))     (die "marker id mismatch"))
              (recur rest out nil))

            ;; Inside an open block: include line iff block resolves "on"
            (seq stack)
            (let [{:keys [kind id inverse?]} (first stack)
                  on? (case kind
                        :feature (feature-on? features id inverse?)
                        :db      (= id (:db db-choice)))]
              (recur rest (if on? (conj out line) out) stack))

            :else
            (recur rest (conj out line) stack)))))))
