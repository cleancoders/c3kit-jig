(ns c3kit-jig.fetch-spec
  (:require [speclj.core :refer [describe it should should-not should-throw]]
            [c3kit-jig.fetch :as fetch]
            [babashka.fs :as fs]))

(defn- mk-fixture-templates! [root]
  (let [tdir (fs/path root "tiny")]
    (fs/create-dirs tdir)
    (spit (fs/file (fs/path tdir "c3kit-template.edn"))
          (pr-str {:id :tiny
                   :name "Tiny"
                   :description "fixture"
                   :version "0.1.0"
                   :min-cli "0.1.0"
                   :tokens {"acme" {:hyphen true}}
                   :secrets [] :features []
                   :next-steps [{:cmd "cd {{name}}"}]}))
    (spit (fs/file (fs/path tdir "README.md")) "# hi")
    (str root)))

(describe "fetch/from-local-dir"
  (it "copies templates/<id>/ into dest"
    (let [root (str (fs/create-temp-dir))
          _    (mk-fixture-templates! root)
          dest (str (fs/path (fs/create-temp-dir) "out"))]
      (fetch/from-local-dir root "tiny" dest)
      (should (fs/exists? (fs/path dest "c3kit-template.edn")))
      (should (fs/exists? (fs/path dest "README.md")))
      (fs/delete-tree root)
      (fs/delete-tree (fs/parent dest))))

  (it "throws when template dir is missing"
    (let [root (str (fs/create-temp-dir))]
      (should-throw (fetch/from-local-dir root "nope"
                                          (str (fs/path root "dest"))))
      (fs/delete-tree root)))

  (it "from-local-dir omits git-ignored dev cruft from the copy"
    (let [root (fs/create-temp-dir {:prefix "tmpl-"})
          src  (fs/path root "templates" "demo")
          dest (fs/path root "out")]
      (fs/create-dirs (fs/path src ".cpcache"))
      (spit (fs/file (fs/path src ".cpcache" "x.json")) "{}")
      (spit (fs/file (fs/path src "demo.iml")) "<module/>")
      (fs/create-dirs (fs/path src "target"))
      (spit (fs/file (fs/path src "target" "out.txt")) "x")
      (spit (fs/file (fs/path src "mytarget.txt")) "keep me")
      (spit (fs/file (fs/path src "c3kit-template.edn")) "{:id :demo}")
      (fetch/from-local-dir (fs/path root "templates") "demo" dest)
      (should     (fs/exists? (fs/path dest "c3kit-template.edn")))
      (should     (fs/exists? (fs/path dest "mytarget.txt")))
      (should-not (fs/exists? (fs/path dest ".cpcache")))
      (should-not (fs/exists? (fs/path dest "demo.iml")))
      (should-not (fs/exists? (fs/path dest "target")))))

  (it "from-local-dir omits generated build artifacts but keeps cljs source"
    (let [root (fs/create-temp-dir {:prefix "tmpl-"})
          src  (fs/path root "templates" "demo")
          dest (fs/path root "out")]
      (fs/create-dirs (fs/path src "src" "cljs" "acme"))
      (spit (fs/file (fs/path src "src" "cljs" "acme" "core.cljs")) "(ns acme.core)")
      (fs/create-dirs (fs/path src "resources" "public" "cljs"))
      (spit (fs/file (fs/path src "resources" "public" "cljs" "specs.html")) "<html/>")
      (fs/create-dirs (fs/path src "resources" "public" "js" "compiled"))
      (spit (fs/file (fs/path src "resources" "public" "js" "compiled" "main.js")) "//")
      (spit (fs/file (fs/path src "resources" "public" "js" "main.js")) "//")
      (fs/create-dirs (fs/path src "dev" "acme" "ssr"))
      (spit (fs/file (fs/path src "dev" "acme" "ssr" "prerender.cljs")) "(ns acme.ssr.prerender)")
      (spit (fs/file (fs/path src "dev" "acme" "ssr" "prerender_pages.cljs")) "(ns acme.ssr.prerender-pages)")
      (spit (fs/file (fs/path src "c3kit-template.edn")) "{:id :demo}")
      (fetch/from-local-dir (fs/path root "templates") "demo" dest)
                ;; cljs source survives
      (should     (fs/exists? (fs/path dest "src" "cljs" "acme" "core.cljs")))
      (should     (fs/exists? (fs/path dest "dev" "acme" "ssr" "prerender.cljs")))
                ;; generated build output pruned
      (should-not (fs/exists? (fs/path dest "resources" "public" "cljs")))
      (should-not (fs/exists? (fs/path dest "resources" "public" "js" "compiled")))
      (should-not (fs/exists? (fs/path dest "resources" "public" "js" "main.js")))
      (should-not (fs/exists? (fs/path dest "dev" "acme" "ssr" "prerender_pages.cljs")))
      (fs/delete-tree root))))
