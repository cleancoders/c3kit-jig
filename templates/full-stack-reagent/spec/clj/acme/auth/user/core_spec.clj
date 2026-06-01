(ns acme.auth.user.core-spec
  (:require [acme.spec-helper :as spec-helper]
            [acme.auth.user.core :as sut]
            [speclj.core :refer [context describe it should should-not should-not=]]))

(describe "User Core"

  (context "Password Hashing"

    (it "hashes the password (slow)"
      (let [hash1 (sut/hash-password "hellothere")
            hash2 (sut/hash-password "hellothere")]
        (should-not= "hellothere" hash1)
        (should-not= "hellothere" hash2)
        (should-not= hash1 hash2)))

    (it "verifies a hashed password"
      (with-redefs [sut/hash-password  spec-helper/speedy-hash
                    sut/check-password (fn [pw hash] (= (spec-helper/speedy-hash pw) hash))]
        (let [hash1 (sut/hash-password "hellothere")
              hash2 (sut/hash-password "hellothere")]
          (should (sut/verify-password "hellothere" hash1))
          (should (sut/verify-password "hellothere" hash2))
          (should-not (sut/verify-password "wrong" hash1))
          (should-not (sut/verify-password "wrong" hash2))
          (should-not (sut/verify-password nil hash2))
          (should-not (sut/verify-password "wrong" nil))
          (should-not (sut/verify-password nil nil)))))))
