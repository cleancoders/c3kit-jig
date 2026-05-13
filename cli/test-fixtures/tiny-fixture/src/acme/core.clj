(ns acme.core)

;; @c3kit/feature :ssr {
(def has-ssr? true)
;; @c3kit/feature :ssr }

;; @c3kit/db :sqlite {
(def db-impl :sqlite)
;; @c3kit/db :sqlite }
;; @c3kit/db :postgres {
(def db-impl :postgres)
;; @c3kit/db :postgres }

(defn -main [& _]
  (println "hello from acme"))
