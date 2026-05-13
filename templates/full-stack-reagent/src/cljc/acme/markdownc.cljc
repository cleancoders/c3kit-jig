(ns acme.markdownc
  (:require [markdown-to-hiccup.core :as mth]))

(defn- strip-attrs
  "Remove the empty attrs map from a hiccup element if present."
  [node]
  (if (and (vector? node) (map? (second node)))
    (into [(first node)] (drop 2 node))
    node))

(defn ->hiccup
  "Convert a markdown string to a single hiccup element.
  Returns nil for blank/nil input."
  [md]
  (when-not (or (nil? md) (= "" md))
    (let [tree  (mth/md->hiccup md)
          div   (mth/component tree)  ;; [:div {} & children]
          kids  (drop 2 div)]         ;; strip :div and {}
      (when (= 1 (count kids))
        (strip-attrs (first kids))))))

(defonce ^:private registry (atom {}))

(defn register-component! [k component-fn]
  (swap! registry assoc k component-fn))

(defn- replace-node [node]
  (cond
    (and (vector? node) (keyword? (first node)) (contains? @registry (first node)))
    (into [(get @registry (first node))] (rest node))

    (vector? node)
    (mapv replace-node node)

    :else node))

(defn resolve-components [hiccup]
  (replace-node hiccup))
