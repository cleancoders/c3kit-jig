(ns c3kit-create.wizard-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.wizard :as w]))

(defn- with-stdin [text f]
  (with-in-str text (f)))

(describe "wizard/prompt-text"
  (it "uses default when input is empty"
    (with-out-str (with-stdin "\n" #(should= "x" (w/prompt-text "Name" "x" identity)))))

  (it "returns trimmed input"
    (with-out-str (with-stdin "abc\n" #(should= "abc" (w/prompt-text "Name" "x" identity)))))

  (it "re-prompts when validate throws"
    (with-out-str
      (with-stdin "1\nok\n"
                  #(should= "ok"
                            (w/prompt-text "Name" nil
                                           (fn [v]
                                             (when-not (re-matches #"[a-z]+" v)
                                               (throw (ex-info "bad" {})))
                                             v)))))))

(describe "wizard/prompt-yn"
  (it "default Yes"
    (with-out-str (with-stdin "\n" #(should (w/prompt-yn "go" true)))))
  (it "default No"
    (with-out-str (with-stdin "\n" #(should= false (w/prompt-yn "go" false)))))
  (it "explicit y"
    (with-out-str (with-stdin "y\n" #(should (w/prompt-yn "go" false)))))
  (it "explicit n"
    (with-out-str (with-stdin "n\n" #(should= false (w/prompt-yn "go" true))))))

(describe "wizard/prompt-select"
  (it "returns nth option for valid index"
    (with-out-str
      (with-stdin "2\n"
                  #(should= :sqlite
                            (w/prompt-select "Database"
                                             [{:id :datomic-pro :label "Datomic"}
                                              {:id :sqlite      :label "SQLite"}]
                                             :datomic-pro)))))
  (it "returns default on empty"
    (with-out-str
      (with-stdin "\n"
                  #(should= :datomic-pro
                            (w/prompt-select "Database"
                                             [{:id :datomic-pro :label "Datomic"}
                                              {:id :sqlite      :label "SQLite"}]
                                             :datomic-pro))))))
