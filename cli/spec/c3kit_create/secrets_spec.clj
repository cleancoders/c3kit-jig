(ns c3kit-create.secrets-spec
  (:require [speclj.core :refer [describe context it should= should should-not= should-not]]
            [c3kit-create.secrets :as s]
            [clojure.string :as str]))

(describe "c3kit-create.secrets"

  (context "hex"
    (it "produces 2 hex chars per byte"
      (should= 48 (count (s/hex 24))))

    (it "two calls produce different strings"
      (should-not= (s/hex 24) (s/hex 24)))

    (it "string matches /^[0-9a-f]+$/"
      (should (re-matches #"[0-9a-f]+" (s/hex 16)))))

  (context "replace-placeholders"
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

  (context "generate-secret-map"
    (it "returns {placeholder hex} for each secret entry"
      (let [m (s/generate-secret-map [{:placeholder "A" :bytes 4}
                                      {:placeholder "B" :bytes 8}])]
        (should= #{"A" "B"} (set (keys m)))
        (should= 8  (count (get m "A")))
        (should= 16 (count (get m "B")))))

    (it "two calls produce different values for the same placeholder"
      (let [m1 (s/generate-secret-map [{:placeholder "X" :bytes 8}])
            m2 (s/generate-secret-map [{:placeholder "X" :bytes 8}])]
        (should-not= (get m1 "X") (get m2 "X")))))

  (context "apply-secret-map"
    (it "replaces each key with its hex value"
      (should= "k=abcd o=abcd"
               (s/apply-secret-map "k=A o=A" {"A" "abcd"})))

    (it "replaces multiple placeholders"
      (should= "1234 / 5678"
               (s/apply-secret-map "X / Y" {"X" "1234" "Y" "5678"})))))
