(ns acme.repl
  (:require
   [acme.init :as init]
   [acme.main :as main]))

(println "Welcome to the Acme REPL!")
(println "Initializing")
(init/install-legend!)
(main/start-db)
(require '[c3kit.bucket.api :as db])
