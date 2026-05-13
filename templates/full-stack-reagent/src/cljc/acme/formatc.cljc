(ns acme.formatc
  (:require [clojure.pprint :as pprint]
            #?(:cljs [goog.string.format]))
  #?(:cljs (:import (goog.i18n NumberFormat)
                    (goog.i18n.NumberFormat Format))))

#?(:cljs (defn format-price [prefix pennies]
           (if (= 0 (mod pennies 100))
             [:span [:small prefix] (int (/ pennies 100))]
             [:span [:small prefix] (/ pennies 100)])))

#?(:cljs (def number-format (NumberFormat. "#,###")))
(defn commas [num]
  #?(:cljs (.format number-format num)
     :clj  (format "%,d" (or num 0))))

#?(:cljs (def dollars-format (NumberFormat. (.-CURRENCY Format), "USD")))
(defn dollars-and-cents [pennies]
  #?(:cljs (.format dollars-format (/ pennies 100))
     :clj  (format "$%,.2f" (-> pennies (or 0) (/ 100.0)))))

(defn dollars [pennies]
  (str "$" (commas (/ pennies 100))))

(defn pretty-printed [x] (with-out-str (pprint/pprint x)))

(defn f->s [num decimals]
  (when num
    #?(:cljs (str (.toFixed num decimals))
       :clj  (format (str "%,." decimals "f") num))))
