(ns c3kit-create.ui-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.ui :as ui]))

(describe "ui/tty?"
  (it "is a boolean"
    (should (contains? #{true false} (ui/tty?)))))

(describe "ui/colorize"
  (it "wraps in ANSI when color is on"
    (should= "[32mhi[0m" (ui/colorize :green "hi" true)))

  (it "returns plain string when color is off"
    (should= "hi" (ui/colorize :green "hi" false))))

(describe "ui/step / ui/ok / ui/fail"
  (it "step prints the message prefixed with arrow"
    (should= "▸ doing thing
"
             (with-out-str (binding [ui/*color?* false] (ui/step "doing thing")))))

  (it "ok prints check"
    (should= "✓ done
"
             (with-out-str (binding [ui/*color?* false] (ui/ok "done")))))

  (it "fail prints cross to stderr"
    (let [sw (java.io.StringWriter.)]
      (binding [*err* sw
                ui/*color?* false]
        (ui/fail "bad"))
      (should= "✗ bad
" (str sw)))))
