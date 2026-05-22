(ns c3kit-create.fs-spec
  (:require [speclj.core :refer [describe it should should-not should-throw]]
            [c3kit-create.fs :as cfs]
            [babashka.fs :as fs]))

(describe "c3kit-create.fs"

  (it "stage-dir creates a unique temp dir that exists"
    (let [d (cfs/stage-dir)]
      (try (should (fs/exists? d))
           (finally (fs/delete-tree d)))))

  (it "cleanup! deletes the dir even if missing"
    (let [d (cfs/stage-dir)]
      (cfs/cleanup! d)
      (should-not (fs/exists? d)))
    (cfs/cleanup! (str (fs/path (fs/temp-dir) "nonexistent-c3k"))))

  (it "same-filesystem? always true for two paths under temp"
    (let [a (str (fs/create-temp-dir))
          b (str (fs/create-temp-dir))]
      (should (cfs/same-filesystem? a b))
      (fs/delete-tree a)
      (fs/delete-tree b)))

  (it "move-into-place! atomic move when source and target share filesystem"
    (let [stage (cfs/stage-dir)
          src   (fs/path stage "scaffold")
          tgt   (fs/path stage "final")]
      (fs/create-dirs src)
      (spit (fs/file (fs/path src "hello.txt")) "hi")
      (cfs/move-into-place! (str src) (str tgt))
      (should (fs/exists? tgt))
      (should (fs/exists? (fs/path tgt "hello.txt")))
      (should-not (fs/exists? src))
      (cfs/cleanup! stage)))

  (it "move-into-place! throws when target already exists"
    (let [stage (cfs/stage-dir)
          src   (fs/path stage "scaffold")
          tgt   (fs/path stage "final")]
      (fs/create-dirs src)
      (fs/create-dirs tgt)
      (should-throw (cfs/move-into-place! (str src) (str tgt)))
      (cfs/cleanup! stage))))
