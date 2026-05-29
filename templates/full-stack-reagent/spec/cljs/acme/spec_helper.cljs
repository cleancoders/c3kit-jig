(ns acme.spec-helper
  (:require-macros [speclj.core :refer [redefs-around should-have-invoked stub]])
  (:refer-clojure :exclude [flush])
  (:require [acme.core :as core]
            [acme.init :as init]
            [acme.page :as page]
            [c3kit.apron.corec :as ccc] ;; Brings in js/ReactTestUtils
            [c3kit.wire.js :as wjs]
            [cljsjs.react.dom.test-utils]
            [speclj.core]
            [taoensso.timbre :as timbre]))

;; Silence c3kit.apron.log / timbre output during specs. The cljs default
;; appender is :console (not :println), so disabling :println does nothing —
;; raising min-level filters at the source regardless of appender.
(timbre/set-min-level! :report)
(init/install-reagent-db-atom!)
(init/install-legend!)
(init/configure-api!)

(defn stub-google-api! []
  (let [auth2 {:attachClickHandler ccc/noop}]
    (set! js/gapi (clj->js {:load  ccc/noop
                            :auth2 {:init (constantly auth2)}}))))

(defn stub-goto! []
  (redefs-around [wjs/redirect! (stub :wjs/redirect!)
                  core/goto! (stub :goto!)]))

(defmethod page/render :helper/blank [_] [:p "Spec helper page"])
