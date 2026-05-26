(ns c3kit-jig.secrets
  (:require [clojure.string :as str])
  (:import [java.security SecureRandom]))

(defn hex
  "Return `bytes` of secure-random data, hex-encoded."
  [bytes]
  (let [sr  (SecureRandom.)
        buf (byte-array bytes)]
    (.nextBytes sr buf)
    (->> buf
         (map #(format "%02x" (bit-and ^long % 0xff)))
         (apply str))))

(defn generate-secret-map
  "Build {placeholder hex} from manifest :secrets entries.
   Caller can apply via `apply-secret-map` and also surface the map in
   .c3kit-jig-context.edn for hook scripts."
  [secrets]
  (into {} (for [{:keys [placeholder bytes]} secrets]
             [placeholder (hex bytes)])))

(defn apply-secret-map
  "Replace each `placeholder` in `text` with its hex value from `secret-map`."
  [text secret-map]
  (reduce-kv (fn [s placeholder hex] (str/replace s placeholder hex))
             text secret-map))

(defn replace-placeholders
  "Generate hex secrets for each placeholder in `secrets`, replace inline."
  [text secrets]
  (apply-secret-map text (generate-secret-map secrets)))
