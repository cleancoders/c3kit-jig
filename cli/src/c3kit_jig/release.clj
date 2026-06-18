(ns c3kit-jig.release
  (:require [clojure.string :as str]))

(defn changes-top-heading
  "First line of `changes` starting with \"### \", or nil."
  [changes]
  (->> (str/split-lines changes)
       (filter #(str/starts-with? % "### "))
       first))

(defn release-blockers
  "Return a vector of human-readable reasons a release is blocked.
   Empty vector => clear to tag and push.
   Expects {:version :changes :dirty? :tag-exists?}."
  [{:keys [version changes dirty? tag-exists?]}]
  (let [top (changes-top-heading changes)]
    (cond-> []
      dirty?
      (conj "working tree is dirty; commit or stash first")
      (not= top (str "### " version))
      (conj (str "CHANGES.md top heading is " (pr-str top)
                 ", expected \"### " version "\""))
      tag-exists?
      (conj (str "tag " version " already exists")))))
