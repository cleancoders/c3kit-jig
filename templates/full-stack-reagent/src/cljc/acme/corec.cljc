(ns acme.corec
  "Common core code.  This file should have minimal dependencies."
  (:require [c3kit.bucket.hashid :as hashid]))

(def hashid-salt "c3kit hashid salt")
(def hashid-min-length 10) ;; with default alphabet (62 chars), hashids of length 10 should give 8x10e17 possibilities
(def hashid-configured-fns (hashid/hashid-fns hashid-salt hashid-min-length))
(def id->hash (:id->hash hashid-configured-fns))
(def hash->id (:hash->id hashid-configured-fns))
(defn hashid [entity] (id->hash (:id entity)))
