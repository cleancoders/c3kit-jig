(ns c3kit-jig.release-spec
  (:require [speclj.core :refer [describe it should= should-contain should-be-nil]]
            [c3kit-jig.release :as release]))

(describe "c3kit-jig.release"

  (it "changes-top-heading returns the first ### heading"
    (should= "### 0.1.0"
             (release/changes-top-heading "### 0.1.0\n * a thing\n\n### 0.0.9\n * old")))

  (it "changes-top-heading returns nil when there is no heading"
    (should-be-nil (release/changes-top-heading "no headings here\n")))

  (it "release-blockers is empty when everything is in order"
    (should= []
             (release/release-blockers {:version     "0.1.0"
                                        :changes     "### 0.1.0\n * a thing"
                                        :dirty?      false
                                        :tag-exists? false})))

  (it "release-blockers flags a dirty working tree"
    (should-contain "working tree is dirty; commit or stash first"
                    (release/release-blockers {:version     "0.1.0"
                                               :changes     "### 0.1.0\n * a"
                                               :dirty?      true
                                               :tag-exists? false})))

  (it "release-blockers flags a CHANGES heading that does not match VERSION"
    (should-contain "CHANGES.md top heading is \"### 0.0.9\", expected \"### 0.1.0\""
                    (release/release-blockers {:version     "0.1.0"
                                               :changes     "### 0.0.9\n * a"
                                               :dirty?      false
                                               :tag-exists? false})))

  (it "release-blockers flags an existing tag"
    (should-contain "tag 0.1.0 already exists"
                    (release/release-blockers {:version     "0.1.0"
                                               :changes     "### 0.1.0\n * a"
                                               :dirty?      false
                                               :tag-exists? true}))))
