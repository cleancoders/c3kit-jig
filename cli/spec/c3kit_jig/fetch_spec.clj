(ns c3kit-jig.fetch-spec
  (:require [speclj.core :refer [describe it should should-throw]]
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
      (fs/delete-tree root))))
