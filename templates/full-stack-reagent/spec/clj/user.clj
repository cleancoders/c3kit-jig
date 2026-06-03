(ns user
  (:require [taoensso.timbre :as timbre]))

;; Clojure auto-loads user.clj from the classpath at startup, so this runs before
;; speclj or any spec namespace. c3kit.wire.spec-helper calls (log/warn!) at load
;; time, which emits a timestamped REPORT line ("Setting log level: :warn").
;;
;; Suppress only that line: disable the println appender, force spec-helper to
;; load so its load-time log fires while muted, then restore the original
;; appenders. This keeps normal logging intact for everything else, including
;; `clj -M:test:run`.
(let [appenders (:appenders timbre/*config*)]
  (timbre/merge-config! {:appenders {:println {:enabled? false}}})
  (require 'c3kit.wire.spec-helper)
  (timbre/merge-config! {:appenders appenders}))
