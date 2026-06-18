(ns c3kit-jig.update-check-spec
  (:require [speclj.core :refer [describe it should= should-be-nil should should-not should-contain]]
            [c3kit-jig.update-check :as uc]))

(describe "c3kit-jig.update-check"

  (it "update-message returns a notice mentioning both versions and upgrade when newer"
    (let [msg (uc/update-message "1.0.0" "1.2.0")]
      (should-contain "1.0.0" msg)
      (should-contain "1.2.0" msg)
      (should-contain "upgrade" msg)))

  (it "update-message is nil when equal or older"
    (should-be-nil (uc/update-message "1.2.0" "1.2.0"))
    (should-be-nil (uc/update-message "1.2.0" "1.1.0")))

  (it "update-message is nil when latest is nil"
    (should-be-nil (uc/update-message "1.0.0" nil)))

  (it "stale? is true when checked-at is nil"
    (should (uc/stale? nil 1000 100)))

  (it "stale? is true when older than ttl"
    (should (uc/stale? 0 101 100)))

  (it "stale? is false when within ttl"
    (should-not (uc/stale? 50 100 100)))

  (it "parse-cache reads a map and rejects non-maps / garbage"
    (should= {:checked-at 5 :latest-tag "1.2.0"}
             (uc/parse-cache "{:checked-at 5 :latest-tag \"1.2.0\"}"))
    (should-be-nil (uc/parse-cache "42"))
    (should-be-nil (uc/parse-cache "}{ not edn")))

  (it "render-cache round-trips with parse-cache"
    (let [m {:checked-at 5 :latest-tag "1.2.0"}]
      (should= m (uc/parse-cache (uc/render-cache m)))))

  (it "disabled-by-env? is true only for a non-blank value"
    (should-not (uc/disabled-by-env? nil))
    (should-not (uc/disabled-by-env? ""))
    (should (uc/disabled-by-env? "1"))
    (should (uc/disabled-by-env? "yes"))))
