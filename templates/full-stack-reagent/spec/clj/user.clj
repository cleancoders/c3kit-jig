(ns user
  (:require [taoensso.timbre :as timbre]))

;; Disable the println appender before any spec file loads.
;; Clojure auto-loads user.clj from the classpath at startup, so this runs before
;; speclj or any spec namespace (including c3kit.wire.spec-helper, which calls
;; log/warn! at load time and would otherwise emit a timestamped REPORT line).
(timbre/merge-config! {:appenders {:println {:enabled? false}}})
