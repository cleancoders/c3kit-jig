(ns c3kit-verify.checks-spec
  (:require [speclj.core :refer [describe it should=]]
            [c3kit-verify.checks :as sut]))

(describe "harness test runner"
          (it "runs" (should= 1 1)))
