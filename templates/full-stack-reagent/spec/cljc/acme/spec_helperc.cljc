(ns acme.spec-helperc
  (:require #?(:clj  [speclj.core :refer [it should-have-invoked stub]]
               :cljs [speclj.core])))

#?(:clj (defmacro it-routes
          "Tests a client side route"
          [path page-key & body]
          `(it ~path
             (with-redefs [acme.routes/load-page! (stub :load-page!)]
               (acme.routes/dispatch! ~path)
               (should-have-invoked :load-page! {:with [~page-key]})
               ~@body))))
