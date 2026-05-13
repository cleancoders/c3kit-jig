(ns c3kit-create.secrets
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

(defn replace-placeholders
  "Replace each :placeholder with a freshly generated hex secret of :bytes length.
   Same placeholder occurring multiple times gets the same secret."
  [text secrets]
  (reduce (fn [s {:keys [placeholder bytes]}]
            (let [secret (hex bytes)]
              (str/replace s placeholder secret)))
          text secrets))
