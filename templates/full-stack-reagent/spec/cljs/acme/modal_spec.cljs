(ns acme.modal-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-not-select should-select]]
                   [speclj.core :refer [around before describe it should-be-nil should=]])
  (:require [acme.modal :as modal]
            [acme.page :as page]
            [c3kit.apron.log :as log]
            [c3kit.wire.spec-helper :as wire-helper]
            [speclj.core]))

(describe "Modal"
  (wire-helper/with-root-dom)
  (around [it] (log/capture-logs (it)))
  (before (page/clear!)
          (wire-helper/render [modal/modal]))

  (it "not open by default"
    (should-not-select "#-modal"))

  (it "default"
    (modal/install! :blah)
    (wire-helper/flush)
    (should-select "#-modal")
    (should-select "#-default-modal"))

  (it "hello"
    (modal/install! :modal/hello)
    (wire-helper/flush)
    (should-select "#-modal")
    (should-select "#-hello-modal"))

  (it "closing"
    (modal/install! :modal/hello)
    (wire-helper/flush)

    (modal/close!)
    (wire-helper/flush)

    (should-not-select "#-modal")
    (should-not-select "#-hello-modal")
    (should-be-nil @modal/state))

  (it "clicking backdrop closes"
    (modal/install! :modal/hello)
    (wire-helper/flush)
    (wire-helper/click! "#-modal-overlay")
    (should-not-select "#-modal"))

  (it "on-close action"
    (let [calls (atom 0)]
      (modal/install! :modal/hello :on-close #(swap! calls inc))
      (wire-helper/flush)
      (modal/close!)
      (should= 1 @calls))))


