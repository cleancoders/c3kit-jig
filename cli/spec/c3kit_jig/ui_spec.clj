(ns c3kit-jig.ui-spec
  (:require [speclj.core :refer [describe context it should= should]]
            [c3kit-jig.ui :as ui]))

(describe "c3kit-jig.ui"

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
" (str sw)))))

          (context "next-steps"
            (it "prints nothing when steps are empty"
                (should= "" (with-out-str (ui/next-steps [] "my-app"))))

            (it "prints header and commands, substituting {{name}}"
                (should= "
Next steps:
  cd my-app
  bin/db  # start db
"
                         (with-out-str
                           (binding [ui/*color?* false]
                             (ui/next-steps [{:cmd "cd {{name}}" :doc nil}
                                             {:cmd "bin/db" :doc "start db"}]
                                            "my-app")))))))
