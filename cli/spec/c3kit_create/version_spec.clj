(ns c3kit-create.version-spec
  (:require [speclj.core :refer [describe it should=]]
            [c3kit-create.version :as v]))

(describe "version/current"
  (it "returns the current CLI semver as a string"
    (should= "0.1.0-SNAPSHOT" (v/current))))
