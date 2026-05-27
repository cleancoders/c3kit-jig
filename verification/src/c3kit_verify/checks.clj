(ns c3kit-verify.checks
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

;; Pure cores and effectful shells are added in later tasks.
