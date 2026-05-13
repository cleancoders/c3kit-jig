(ns c3kit-create.ui-spec
  (:require [speclj.core :refer [describe context it should= should should-not]]
            [c3kit-create.ui :as ui]))

(describe "c3kit-create.ui"

  (it "tty? is a boolean"
    (should (contains? #{true false} (ui/tty?))))

  (it "colorize wraps in ANSI when color is on"
    (should= "[32mhi[0m" (ui/colorize :green "hi" true)))

  (it "colorize returns plain string when color is off"
    (should= "hi" (ui/colorize :green "hi" false)))

  (context "step / ok / fail"
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
" (str sw))))))
