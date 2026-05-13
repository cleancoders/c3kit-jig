(ns c3kit-create.version-spec
  (:require [speclj.core :refer [describe context it should= should]]
            [c3kit-create.version :as v]))

(describe "c3kit-create.version"

  (context "current"
    (it "returns the current CLI semver as a string"
      (should= "0.1.0-SNAPSHOT" (v/current))))

  (context "semver-compare"
    (it "compares standard releases"
      (should= -1 (v/semver-compare "0.1.0" "0.2.0"))
      (should=  0 (v/semver-compare "0.1.0" "0.1.0"))
      (should=  1 (v/semver-compare "0.2.0" "0.1.0"))))

  (context "sha256"
    (it "hashes a string to 64 hex chars"
      (should= 64 (count (v/sha256 "hello")))))

  (context "check-and-download!"
    (it "no-ops when latest matches current"
      (with-redefs [v/fetch-latest-tag! (constantly "cli-v0.1.0-SNAPSHOT")]
        (should= :up-to-date (v/check-and-download! "/tmp/whatever"))))))
