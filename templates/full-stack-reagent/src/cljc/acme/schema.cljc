(ns acme.schema
  (:require [acme.bg-task :as bg-task]
            ;; @c3kit/feature :auth {
            [acme.auth.user.schema :as user.schema]
            ;; @c3kit/feature :auth }
            ))

;; @c3kit/feature !:auth {
(def full
  (concat
    [bg-task/bg-task]))
;; @c3kit/feature !:auth }

;; @c3kit/feature :auth {
(def full
  (concat
    [bg-task/bg-task]
    user.schema/all))
;; @c3kit/feature :auth }