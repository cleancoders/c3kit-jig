(ns c3kit-create.secrets-spec
  (:require [speclj.core :refer [describe it should= should should-not= should-not]]
            [c3kit-create.secrets :as s]
            [clojure.string :as str]))

(describe "secrets/hex"
  (it "produces 2 hex chars per byte"
    (should= 48 (count (s/hex 24))))

  (it "two calls produce different strings"
    (should-not= (s/hex 24) (s/hex 24)))

  (it "string matches /^[0-9a-f]+$/"
    (should (re-matches #"[0-9a-f]+" (s/hex 16)))))

(describe "secrets/replace-placeholders"
  (it "replaces a single placeholder with one consistent hex value"
    (let [out (s/replace-placeholders "key=ACME_DEV_SECRET other=ACME_DEV_SECRET"
                                       [{:placeholder "ACME_DEV_SECRET" :bytes 8}])
          [_ a b] (re-find #"key=([0-9a-f]+) other=([0-9a-f]+)" out)]
      (should= a b)))

  (it "different placeholders get different secrets"
    (let [out (s/replace-placeholders "a=A b=B"
                                       [{:placeholder "A" :bytes 8}
                                        {:placeholder "B" :bytes 8}])
          [_ a b] (re-find #"a=([0-9a-f]+) b=([0-9a-f]+)" out)]
      (should-not= a b)))

  (it "warns but does not throw when placeholder absent (returns input)"
    (should= "hello" (s/replace-placeholders "hello"
                                              [{:placeholder "MISSING" :bytes 8}]))))
