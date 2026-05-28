(ns acme.schema.full
  (:require [acme.schema.bg-task :as bg-task]
            ;; @c3kit/feature :auth {
            [acme.schema.user :as user]
            ;; @c3kit/feature :auth }
            ))

;; @c3kit/feature !:auth {
(def full [bg-task/bg-task])
;; @c3kit/feature !:auth }
;; @c3kit/feature :auth {
(def full (concat [bg-task/bg-task] user/all))
;; @c3kit/feature :auth }
