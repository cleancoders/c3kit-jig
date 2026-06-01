(ns acme.http-util-spec
  (:require [acme.http-util :as sut]
            [speclj.core :refer [describe it should should-not]]))

(describe "wants-markdown?"

  (it "true for text/markdown"
    (should (sut/wants-markdown? {:headers {"accept" "text/markdown"}})))

  (it "true for text/markdown with q-values and fallbacks"
    (should (sut/wants-markdown? {:headers {"accept" "text/markdown,*/*;q=0.8"}})))

  (it "true for text/plain"
    (should (sut/wants-markdown? {:headers {"accept" "text/plain"}})))

  (it "false for text/html"
    (should-not (sut/wants-markdown? {:headers {"accept" "text/html"}})))

  (it "false for missing Accept header"
    (should-not (sut/wants-markdown? {})))

  (it "false for application/json"
    (should-not (sut/wants-markdown? {:headers {"accept" "application/json"}}))))
