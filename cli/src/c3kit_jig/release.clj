(ns c3kit-jig.release
  "Release pre-flight checks and the tag-and-push action for the CLI."
  (:require [babashka.process :as process]
            [clojure.string :as str]))

(defn changes-top-heading
  "First line of `changes` starting with \"### \", or nil."
  [changes]
  (->> (str/split-lines changes)
       (filter #(str/starts-with? % "### "))
       first))

(defn- heading-mismatch-message [top version]
  (str "CHANGES.md top heading is " (pr-str top) ", expected \"### " version "\""))

(defn release-blockers
  "Return a vector of human-readable reasons a release is blocked.
   Empty vector => clear to tag and push.
   Expects {:version :changes :dirty? :tag-exists?}."
  [{:keys [version changes dirty? tag-exists?]}]
  (let [top (changes-top-heading changes)]
    (cond-> []
      dirty? (conj "working tree is dirty; commit or stash first")
      (not= top (str "### " version)) (conj (heading-mismatch-message top version))
      tag-exists? (conj (str "tag " version " already exists")))))

(defn- git-dirty? []
  (-> (process/shell {:out :string} "git" "status" "--short")
      :out str/blank? not))

(defn- git-tag-exists? [version]
  (-> (process/shell {:out :string :err :string :continue true}
                     "git" "rev-parse" "--verify" "--quiet" (str "refs/tags/" version))
      :exit zero?))

(defn release!
  "Read the root VERSION + CHANGES, run pre-flight checks, and — when clear —
   tag the version and push it (CI builds + publishes). Run from `cli/`.
   Prints blockers and exits non-zero when the release is not clear."
  []
  (let [version  (str/trim (slurp "../VERSION"))
        blockers (release-blockers {:version     version
                                    :changes     (slurp "../CHANGES.md")
                                    :dirty?      (git-dirty?)
                                    :tag-exists? (git-tag-exists? version)})]
    (if (seq blockers)
      (do (doseq [b blockers] (println "ERROR:" b))
          (System/exit 1))
      (do (process/shell "git" "tag" version)
          (process/shell "git" "push" "origin" version)
          (println (str "tagged + pushed " version " — CI will build and publish assets"))))))
