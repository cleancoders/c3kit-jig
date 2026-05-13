(ns c3kit-create.features
  (:require [clojure.string :as str]))

(def ^:private LINE-EQ-RE
  #"@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*=\s*(.*)$")

(def ^:private DB-LINE-EQ-RE
  #"@c3kit/db\s+:([A-Za-z][A-Za-z0-9-]*)\s*=\s*(.*)$")

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

(defn- resolve-line-eq [line features]
  (let [[_ inv id-str code] (re-find LINE-EQ-RE line)]
    (if (feature-on? features (keyword id-str) (some? inv)) code ::drop)))

(defn- resolve-db-line-eq [line db-choice]
  (let [[_ id-str code] (re-find DB-LINE-EQ-RE line)]
    (if (= (keyword id-str) (:db db-choice)) code ::drop)))

(defn- block-open [line]
  (when-let [[_ inv id-str] (re-find BLOCK-OPEN-RE line)]
    {:kind :feature :id (keyword id-str) :inverse? (some? inv)}))

(defn- block-close [line]
  (when-let [[_ inv id-str] (re-find BLOCK-CLOSE-RE line)]
    {:kind :feature :id (keyword id-str) :inverse? (some? inv)}))

(defn- db-open [line]
  (when-let [[_ id-str] (re-find DB-OPEN-RE line)]
    {:kind :db :id (keyword id-str)}))

(defn- db-close [line]
  (when-let [[_ id-str] (re-find DB-CLOSE-RE line)]
    {:kind :db :id (keyword id-str)}))

(defn- line-eq? [line stack]   (and (empty? stack) (re-find LINE-EQ-RE line)))
(defn- db-line-eq? [line stack] (and (empty? stack) (re-find DB-LINE-EQ-RE line)))
(defn- open-marker [line]      (or (block-open line) (db-open line)))
(defn- close-marker [line]     (or (block-close line) (db-close line)))

(defn- apply-line-eq [out stack line features]
  (let [r (resolve-line-eq line features)]
    [(if (= r ::drop) out (conj out r)) stack]))

(defn- apply-db-line-eq [out stack line db-choice]
  (let [r (resolve-db-line-eq line db-choice)]
    [(if (= r ::drop) out (conj out r)) stack]))

(defn- push-open [out stack line]
  (when (seq stack) (die "nested marker"))
  [out [(open-marker line)]])

(defn- pop-close [out stack line]
  (let [close (close-marker line)
        [open] stack]
    (when-not open                            (die "close without open"))
    (when-not (= (:kind open) (:kind close))  (die "marker kind mismatch"))
    (when-not (= (:id open)   (:id close))    (die "marker id mismatch"))
    [out nil]))

(defn- emit-inside-block [out stack line features db-choice]
  (let [{:keys [kind id inverse?]} (first stack)
        on? (case kind
              :feature (feature-on? features id inverse?)
              :db      (= id (:db db-choice)))]
    [(if on? (conj out line) out) stack]))

(defn- step-line [out stack line features db-choice]
  (cond
    (line-eq? line stack)    (apply-line-eq      out stack line features)
    (db-line-eq? line stack) (apply-db-line-eq   out stack line db-choice)
    (open-marker line)       (push-open          out stack line)
    (close-marker line)      (pop-close          out stack line)
    (seq stack)              (emit-inside-block  out stack line features db-choice)
    :else                    [(conj out line) stack]))

(defn strip
  "Strip feature/db markers from text. Returns rewritten string.

   features  — map of feature-id → bool
   db-choice — map with optional :db keyword for db-marker selection"
  [text features db-choice]
  (loop [in    (str/split-lines text)
         out   []
         stack nil]
    (if (empty? in)
      (do (when (seq stack) (die (str "unclosed marker: " (first stack))))
          (str/join "\n" out))
      (let [[out' stack'] (step-line out stack (first in) features db-choice)]
        (recur (rest in) out' stack')))))
