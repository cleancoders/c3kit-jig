(ns acme.layout-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-not-select should-select]]
                   [speclj.core :refer [around before describe it should-contain with-stubs]])
  (:require [acme.layout :as sut]
            [acme.modal :as modal]
            [acme.page :as page]
            [acme.test-data :as test-data]
            ;; @c3kit/feature :auth {
            [acme.auth.user :as user]
            ;; @c3kit/feature :auth }
            [c3kit.apron.log :as log]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.spec-helper :as wire-helper]))

(defmethod page/render :layout/test [_] "Layout Test")
(defmethod modal/modal-content :layout/modal [] "Layout Modal Test")

(describe "Layout"
          (with-stubs)
          (wire-helper/with-root-dom)
          (around [it] (log/capture-logs (it)))
  ;; @c3kit/feature :auth {
          (test-data/with-memory-kinds :user)
  ;; @c3kit/feature :auth }
          (before (page/clear!)
                  (flash/clear!)
          ;; @c3kit/feature :auth = (user/install! @test-data/road-runner)
                  (page/install-page! :layout/test)
                  (wire-helper/render [sut/default]))

          (it "structure"
              (should-select "#content")
              (should-contain "Layout Test" (wire-helper/html)))

          (it "flash"
              (should-not-select ".flash-root")
              (flash/add-success! "Yes!")
              (wire-helper/flush)
              (should-select ".flash-root"))

          (it "modal"
              (should-not-select "#-modal")
              (modal/install! :layout/modal)
              (wire-helper/flush)
              (should-select "#-modal"))

          (it "spinner"
              (should-not-select ".site-spinner")
              (swap! ajax/active-ajax-requests inc)
              (wire-helper/flush)
              (should-select ".site-spinner")))
