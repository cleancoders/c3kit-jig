(ns acme.email-spec
  (:import (com.amazonaws.services.simpleemail.model Destination Message))
  (:require [acme.email :as sut] [speclj.core :refer :all]))

(describe "Email"

  (context "ses-destination"

    (it "just a to address"
      (let [^Destination dest (sut/ses-destination "micah@cleancoders.com" nil nil)]
        (should= ["micah@cleancoders.com"] (.getToAddresses dest))
        (should= [] (.getCcAddresses dest))
        (should= [] (.getBccAddresses dest))))

    (it "all addresses single"
      (let [^Destination dest (sut/ses-destination "micah@cleancoders.com"
                                                   "micahmartin@gmail.com"
                                                   "micah@airworthy.co")]
        (should= ["micah@cleancoders.com"] (.getToAddresses dest))
        (should= ["micahmartin@gmail.com"] (.getCcAddresses dest))
        (should= ["micah@airworthy.co"] (.getBccAddresses dest))))

    (it "all addresses multiple"
      (let [^Destination dest (sut/ses-destination ["micah@cleancoders.com" "micah2@cleancoders.com"]
                                                   ["micahmartin@gmail.com" "micahmartin2@gmail.com"]
                                                   ["micah@airworthy.co" "micah2@airworthy.co"])]
        (should= ["micah@cleancoders.com" "micah2@cleancoders.com"] (.getToAddresses dest))
        (should= ["micahmartin@gmail.com" "micahmartin2@gmail.com"] (.getCcAddresses dest))
        (should= ["micah@airworthy.co" "micah2@airworthy.co"] (.getBccAddresses dest))))

    (it "->email-list"
      (should= ["abc"] (sut/->email-list "abc"))
      (should= ["abc"] (sut/->email-list ["abc"]))
      (should= [] (sut/->email-list nil))
      (should= ["abc" "xyz"] (sut/->email-list ["abc" "xyz"]))
      (should= ["abc" "xyz"] (sut/->email-list ["abc" nil "xyz"]))
      (should= ["abc" "xyz"] (sut/->email-list ["abc" 1 "xyz"])))

    )

  (context "ses-message"

    (it "subject and just text"
      (let [^Message message (sut/ses-message "Hello" "Hi There!" nil)]
        (should= "Hello" (.getData (.getSubject message)))
        (should= "Hi There!" (.getData (.getText (.getBody message))))
        (should= "<p>Hi There!</p>\n" (.getData (.getHtml (.getBody message))))))

    (it "subject and just HTML"
      (let [^Message message (sut/ses-message "Hello" nil "<h1>Wave</h1>")]
        (should= "Hello" (.getData (.getSubject message)))
        (should= "Please see the HTML content of this email." (.getData (.getText (.getBody message))))
        (should= "<h1>Wave</h1>" (.getData (.getHtml (.getBody message))))))

    (it "text and HTML"
      (let [^Message message (sut/ses-message "Hello" "Hi There!" "<h1>Wave</h1>")]
        (should= "Hello" (.getData (.getSubject message)))
        (should= "Hi There!" (.getData (.getText (.getBody message))))
        (should= "<h1>Wave</h1>" (.getData (.getHtml (.getBody message))))))

    )

  )
