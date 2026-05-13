(ns c3kit-create.wizard
  (:require [clojure.string :as str]
            [c3kit-create.ui :as ui]))

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

(defn prompt-select [label options default-id]
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
