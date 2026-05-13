(ns c3kit-create.version
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def ^:const CURRENT "0.1.0-SNAPSHOT")

(defn current [] CURRENT)

(def RELEASES-URL
  "https://api.github.com/repos/cleancoders/c3kit-starter/releases/latest")

(defn semver-compare [a b]
  (let [parse #(mapv parse-long (str/split (first (str/split % #"-")) #"\."))
        [a1 a2 a3] (parse a)
        [b1 b2 b3] (parse b)]
    (compare [a1 a2 a3] [b1 b2 b3])))

(defn sha256 [^String s]
  (let [md (MessageDigest/getInstance "SHA-256")
        bs (.digest md (.getBytes s))]
    (->> bs
         (map #(format "%02x" (bit-and ^long % 0xff)))
         (apply str))))

(defn fetch-latest-tag! []
  (let [resp (http/get RELEASES-URL {:throw false})]
    (when-not (= 200 (:status resp))
      (throw (ex-info (str "GitHub releases unreachable: HTTP " (:status resp))
                      {:upgrade? true})))
    (let [body (:body resp)
          tag  (second (re-find #"\"tag_name\"\s*:\s*\"([^\"]+)\"" body))]
      (when-not tag (throw (ex-info "no tag in releases response"
                                    {:upgrade? true})))
      tag)))

(defn check-and-download!
  "Download latest uberscript to `binary-path.new`, verify SHA, atomic mv.
   Returns :up-to-date or :upgraded."
  [^String binary-path]
  (let [tag (fetch-latest-tag!)]
    (if (= tag (str "cli-v" CURRENT))
      :up-to-date
      (let [base (str "https://github.com/cleancoders/c3kit-starter/releases/download/" tag)
            ub   (str base "/c3kit-create.bb")
            sh   (str base "/c3kit-create.bb.sha256")
            new-path (str binary-path ".new")
            body (:body (http/get ub  {:throw false}))
            want (str/trim (:body (http/get sh {:throw false})))]
        (when-not (= want (sha256 body))
          (throw (ex-info "SHA256 mismatch on downloaded uberscript"
                          {:upgrade? true})))
        (spit new-path body)
        (.setExecutable (fs/file new-path) true)
        (fs/move new-path binary-path {:replace-existing true})
        :upgraded))))
