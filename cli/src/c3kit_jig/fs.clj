(ns c3kit-jig.fs
  (:require [babashka.fs :as fs])
  (:import [java.nio.file Files]
           [java.util UUID]))

(defn stage-dir
  "Create a fresh staging dir under $TMPDIR for this run."
  []
  (let [name (str "c3kit-jig-" (UUID/randomUUID))
        d    (fs/path (fs/temp-dir) name)]
    (fs/create-dirs d)
    (str d)))

(defn cleanup!
  "Recursively delete a staging dir if it exists. No-op if missing."
  [^String path]
  (when (and path (fs/exists? path))
    (fs/delete-tree path)))

(defn same-filesystem?
  "True if two paths sit on the same filesystem store."
  [^String a ^String b]
  (try
    (= (Files/getFileStore (fs/path a))
       (Files/getFileStore (fs/path b)))
    (catch Exception _ false)))

(defn- copy-tree! [^String src ^String tgt]
  (fs/copy-tree src tgt)
  (fs/delete-tree src))

(defn move-into-place!
  "Move source scaffold dir to final target. Throws if target already exists.
   Falls back to copy+delete on cross-filesystem moves."
  [^String src ^String tgt]
  (when (fs/exists? tgt)
    (throw (ex-info (str "target already exists: " tgt)
                    {:collision? true})))
  (if (same-filesystem? src (str (fs/parent tgt)))
    (fs/move src tgt)
    (copy-tree! src tgt)))
