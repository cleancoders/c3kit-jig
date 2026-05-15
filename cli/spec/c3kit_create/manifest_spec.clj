(ns c3kit-create.manifest-spec
  (:require [speclj.core :refer [describe it should= should-throw]]
            [c3kit-create.manifest :as m]))

(def MIN-MANIFEST
  {:id           :tiny
   :name         "Tiny"
   :description  "tiny test fixture"
   :version      "0.1.0"
   :min-cli      "0.1.0"
   :tokens       {"acme" {:hyphen true :underscore true :pascal true}}
   :secrets      []
   :features     []
   :next-steps   [{:cmd "cd {{name}}"}]})

(describe "manifest/validate"
  (it "accepts a minimum manifest"
    (should= MIN-MANIFEST (m/validate MIN-MANIFEST "tiny")))

  (it "rejects when :id is not a keyword"
    (should-throw (m/validate (assoc MIN-MANIFEST :id "tiny") "tiny")))

  (it "rejects when :id != dir name"
    (should-throw (m/validate (assoc MIN-MANIFEST :id :other) "tiny")))

  (it "rejects malformed semver"
    (should-throw (m/validate (assoc MIN-MANIFEST :version "0.1") "tiny")))

  (it "rejects empty tokens"
    (should-throw (m/validate (assoc MIN-MANIFEST :tokens {"" {}}) "tiny")))

  (it "rejects duplicate secret placeholders"
    (should-throw (m/validate (assoc MIN-MANIFEST :secrets
                                      [{:placeholder "X" :bytes 8}
                                       {:placeholder "X" :bytes 8}])
                              "tiny")))

  (it "rejects duplicate feature ids"
    (should-throw (m/validate (assoc MIN-MANIFEST :features
                                      [{:id :a :prompt "" :default true}
                                       {:id :a :prompt "" :default true}])
                              "tiny")))

  (it "rejects :db.default not in :db.options"
    (should-throw (m/validate (assoc MIN-MANIFEST :db
                                      {:prompt "DB"
                                       :options [{:id :sqlite :label "SQLite"}]
                                       :default :postgres})
                              "tiny")))

  (it "accepts :namespace-token at manifest root"
    (let [m (assoc MIN-MANIFEST :namespace-token "acme")]
      (should= m (m/validate m "tiny"))))

  (it "accepts :extras on a feature"
    (let [m (assoc MIN-MANIFEST
                   :features [{:id :ssr :prompt "" :default true
                               :extras ["package.json" "resources/prerender/"]}])]
      (should= m (m/validate m "tiny"))))

  (it "rejects :extras escaping template root"
    (let [m (assoc MIN-MANIFEST
                   :features [{:id :ssr :prompt "" :default true
                               :extras ["../etc/passwd"]}])]
      (should-throw (m/validate m "tiny"))))

  (it "accepts :template and :sibling-glob on :db"
    (let [m (assoc MIN-MANIFEST
                   :db {:prompt "DB"
                        :options [{:id :sqlite :label "SQLite"}]
                        :default :sqlite
                        :template "bin/db.template.{{db}}"
                        :sibling-glob "bin/db.template.*"})]
      (should= m (m/validate m "tiny")))))
