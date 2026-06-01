(ns acme.email-spec
  (:require [acme.email :as sut]
            [c3kit.apron.log]
            [speclj.core :refer [context describe it should-contain should=]]))

(describe "Email"

  (context "->email-list"

    (it "converts to a list"
      (should= ["abc"] (sut/->email-list "abc"))
      (should= ["abc"] (sut/->email-list ["abc"]))
      (should= [] (sut/->email-list nil))
      (should= ["abc" "xyz"] (sut/->email-list ["abc" "xyz"]))
      (should= ["abc" "xyz"] (sut/->email-list ["abc" nil "xyz"]))
      (should= ["abc" "xyz"] (sut/->email-list ["abc" 1 "xyz"]))))

  (context "client-send-email"

    (it "to-log client logs subject + body"
      (c3kit.apron.log/capture-logs
       (sut/client-send-email {:client :to-log}
                              {:from "a@b" :to "c@d" :subject "Hi" :text "Body" :html "<p>Body</p>"}))
      (should-contain "Hi" (c3kit.apron.log/captured-logs-str))
      (should-contain "Body" (c3kit.apron.log/captured-logs-str)))))
