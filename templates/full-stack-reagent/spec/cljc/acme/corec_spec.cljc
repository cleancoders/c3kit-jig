(ns acme.corec-spec
  (:require [speclj.core #?(:clj :refer :cljs :refer-macros) [context describe it should should=]]
            [acme.corec :as sut]))

(describe "Corec"

  (context "hashid"

    (it "round-trips ids to short hashes"
      (should= 1 (sut/hash->id (sut/id->hash 1)))
      (should= 42 (sut/hash->id (sut/id->hash 42)))
      (should (>= (count (sut/id->hash 1)) sut/hashid-min-length)))))
