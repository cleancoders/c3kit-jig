(ns acme.corec-spec
  (:require [speclj.core #?(:clj :refer :cljs :refer-macros) [after after-all around around-all before before-all
                                                              context describe focus-context focus-describe focus-it it
                                                              pending should should-be should-be-a should-be-nil
                                                              should-be-same should-contain should-end-with should-fail
                                                              should-have-invoked should-invoke should-not
                                                              should-not-be should-not-be-a should-not-be-nil
                                                              should-not-be-same should-not-contain should-not-end-with
                                                              should-not-have-invoked should-not-invoke
                                                              should-not-start-with should-not-throw should-not=
                                                              should-not== should-start-with should-throw should<
                                                              should<= should= should== should> should>= stub tags
                                                              with with-all with-stubs xit]]
            [acme.corec :as sut]))

(describe "Corec"

  (context "hashid"

    (it "round-trips ids to short hashes"
      (should= 1 (sut/hash->id (sut/id->hash 1)))
      (should= 42 (sut/hash->id (sut/id->hash 42)))
      (should (>= (count (sut/id->hash 1)) sut/hashid-min-length)))))
