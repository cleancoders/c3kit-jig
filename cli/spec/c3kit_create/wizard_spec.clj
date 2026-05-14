(ns c3kit-create.wizard-spec
  (:require [speclj.core :refer [describe context it should= should should-not]]
            [c3kit-create.wizard :as w]))

(defn- with-stdin [text f]
  (with-in-str text (f)))

(defn- silence-err [f]
  (binding [*err* (java.io.StringWriter.)] (f)))

(describe "c3kit-create.wizard"

  (context "prompt-text"
    (it "uses default when input is empty"
      (with-out-str (with-stdin "\n" #(should= "x" (w/prompt-text "Name" "x" identity)))))

    (it "returns trimmed input"
      (with-out-str (with-stdin "abc\n" #(should= "abc" (w/prompt-text "Name" "x" identity)))))

    (it "re-prompts when validate throws"
      (silence-err
        #(with-out-str
           (with-stdin "1\nok\n"
                       (fn [] (should= "ok"
                                       (w/prompt-text "Name" nil
                                                      (fn [v]
                                                        (when-not (re-matches #"[a-z]+" v)
                                                          (throw (ex-info "bad" {})))
                                                        v)))))))))

  (context "prompt-yn"
    (it "default Yes"
      (with-out-str (with-stdin "\n" #(should (w/prompt-yn "go" true)))))
    (it "default No"
      (with-out-str (with-stdin "\n" #(should= false (w/prompt-yn "go" false)))))
    (it "explicit y"
      (with-out-str (with-stdin "y\n" #(should (w/prompt-yn "go" false)))))
    (it "explicit n"
      (with-out-str (with-stdin "n\n" #(should= false (w/prompt-yn "go" true))))))

  (it "prompt-select returns nth option for valid index"
    (with-out-str
      (with-stdin "2\n"
                  #(should= :sqlite
                            (w/prompt-select "Database"
                                             [{:id :datomic-pro :label "Datomic"}
                                              {:id :sqlite      :label "SQLite"}]
                                             :datomic-pro)))))

  (it "prompt-select returns default on empty"
    (with-out-str
      (with-stdin "\n"
                  #(should= :datomic-pro
                            (w/prompt-select "Database"
                                             [{:id :datomic-pro :label "Datomic"}
                                              {:id :sqlite      :label "SQLite"}]
                                             :datomic-pro)))))

  (context "prompt-features"
    (it "prompts y/n per feature using manifest default"
      (with-out-str
        (with-stdin "\n\n"
                    #(should= {:a true :b false}
                              (w/prompt-features
                                [{:id :a :prompt "A?" :default true}
                                 {:id :b :prompt "B?" :default false}]
                                nil)))))

    (it "honors explicit answers"
      (with-out-str
        (with-stdin "n\ny\n"
                    #(should= {:a false :b true}
                              (w/prompt-features
                                [{:id :a :prompt "A?" :default true}
                                 {:id :b :prompt "B?" :default false}]
                                nil)))))

    (it "skips features overridden via CLI"
      (with-out-str
        (with-stdin "n\n"
                    #(should= {:a false :b true}
                              (w/prompt-features
                                [{:id :a :prompt "A?" :default true}
                                 {:id :b :prompt "B?" :default false}]
                                {:b true})))))

    (it "returns empty map when features nil or empty"
      (should= {} (w/prompt-features nil nil))
      (should= {} (w/prompt-features [] nil))))

  (context "prompt-select with gum"
    (it "returns id for picked label when gum available"
      (with-redefs [w/gum-available? (fn [] true)
                    w/gum-choose-one (fn [_h _labels _sel] "SQLite")]
        (should= :sqlite
                 (w/prompt-select "Database"
                                  [{:id :datomic-pro :label "Datomic"}
                                   {:id :sqlite      :label "SQLite"}]
                                  :datomic-pro))))

    (it "passes default label as --selected"
      (let [captured (atom nil)]
        (with-redefs [w/gum-available? (fn [] true)
                      w/gum-choose-one (fn [_h _labels sel]
                                         (reset! captured sel)
                                         "Datomic")]
          (w/prompt-select "Database"
                           [{:id :datomic-pro :label "Datomic"}
                            {:id :sqlite      :label "SQLite"}]
                           :sqlite))
        (should= "SQLite" @captured)))

    (it "falls back to numbered prompt when gum throws"
      (with-redefs [w/gum-available? (fn [] true)
                    w/gum-choose-one (fn [_ _ _] (throw (ex-info "cancel" {})))]
        (with-out-str
          (with-stdin "2\n"
                      #(should= :sqlite
                                (w/prompt-select "Database"
                                                 [{:id :datomic-pro :label "Datomic"}
                                                  {:id :sqlite      :label "SQLite"}]
                                                 :datomic-pro))))))

    (it "falls back to numbered when labels collide"
      (with-redefs [w/gum-available? (fn [] true)
                    w/gum-choose-one (fn [_ _ _] (throw (ex-info "should not be called" {})))]
        (with-out-str
          (with-stdin "\n"
                      #(should= :a
                                (w/prompt-select "Pick"
                                                 [{:id :a :label "Same"}
                                                  {:id :b :label "Same"}]
                                                 :a)))))))

  (context "prompt-features-checkbox"
    (it "uses gum when available; returns ids picked + forced overrides"
      (with-redefs [w/gum-available? (fn [] true)
                    w/gum-choose     (fn [_labels _selected] #{"A?" "C?"})]
        (should= {:a true :b false :c true}
                 (w/prompt-features-checkbox
                   [{:id :a :prompt "A?" :default true}
                    {:id :b :prompt "B?" :default true}
                    {:id :c :prompt "C?" :default false}]
                   nil))))

    (it "honors CLI overrides without prompting for those features"
      (let [pipe-args (atom nil)]
        (with-redefs [w/gum-available? (fn [] true)
                      w/gum-choose     (fn [labels selected]
                                         (reset! pipe-args {:labels (vec labels)
                                                            :selected (set selected)})
                                         #{"B?"})]
          (should= {:a true :b true :c false}
                   (w/prompt-features-checkbox
                     [{:id :a :prompt "A?" :default false}
                      {:id :b :prompt "B?" :default true}
                      {:id :c :prompt "C?" :default true}]
                     {:a true})))
        (should= ["B?" "C?"] (:labels @pipe-args))
        (should= #{"B?" "C?"} (:selected @pipe-args))))

    (it "falls back to yn loop when gum unavailable"
      (with-redefs [w/gum-available? (fn [] false)]
        (with-out-str
          (with-stdin "y\nn\n"
                      #(should= {:a true :b false}
                                (w/prompt-features-checkbox
                                  [{:id :a :prompt "A?" :default false}
                                   {:id :b :prompt "B?" :default true}]
                                  nil))))))

    (it "falls back when gum-choose throws (e.g. user cancels)"
      (with-redefs [w/gum-available? (fn [] true)
                    w/gum-choose     (fn [_ _] (throw (ex-info "cancelled" {})))]
        (with-out-str
          (with-stdin "n\ny\n"
                      #(should= {:a false :b true}
                                (w/prompt-features-checkbox
                                  [{:id :a :prompt "A?" :default true}
                                   {:id :b :prompt "B?" :default false}]
                                  nil))))))

    (it "returns empty map when features nil/empty"
      (should= {} (w/prompt-features-checkbox nil nil))
      (should= {} (w/prompt-features-checkbox [] nil))))

  (context "prompt-db"
    (it "returns chosen id via prompt-select"
      (with-out-str
        (with-stdin "2\n"
                    #(should= :sqlite
                              (w/prompt-db
                                {:prompt  "Database"
                                 :options [{:id :datomic-pro :label "Datomic"}
                                           {:id :sqlite      :label "SQLite"}]
                                 :default :datomic-pro}
                                nil)))))

    (it "returns default on empty input"
      (with-out-str
        (with-stdin "\n"
                    #(should= :datomic-pro
                              (w/prompt-db
                                {:prompt  "Database"
                                 :options [{:id :datomic-pro :label "Datomic"}
                                           {:id :sqlite      :label "SQLite"}]
                                 :default :datomic-pro}
                                nil)))))

    (it "skips prompt when CLI override provided"
      (should= :postgres
               (w/prompt-db
                 {:prompt  "Database"
                  :options [{:id :datomic-pro :label "Datomic"}
                            {:id :postgres    :label "Postgres"}]
                  :default :datomic-pro}
                 :postgres)))

    (it "returns nil when manifest has no :db"
      (should= nil (w/prompt-db nil nil)))))
