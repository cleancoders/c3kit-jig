(ns acme.content.hiccup-registry
  "Process-wide registry that swaps hiccup tags for component functions.

   Workflow:
     1. Register a component fn under a custom tag at startup:
          (register-component! :my-quote my-quote-component)
     2. Server (or client) parses markdown → hiccup, then pipes the result
        through `resolve-components` before rendering / shipping. Any
        `[:my-quote {…}]` nodes get rewritten to `[my-quote-component {…}]`
        so reagent picks them up as components.

   The registry walks arbitrary hiccup — it is not tied to markdown — so
   it composes with any hiccup source the project produces."
  (:require [clojure.walk :as walk]))

(defonce registry (atom {}))

(defn register-component!
  "Associate `component-fn` with the hiccup tag `k`. Subsequent calls to
   `resolve-components` will replace `[k props …]` with `[component-fn props …]`."
  [k component-fn]
  (swap! registry assoc k component-fn))

(defn- replace-node [node]
  (if (and (vector? node)
           (keyword? (first node))
           (contains? @registry (first node)))
    (into [(get @registry (first node))] (rest node))
    node))

(defn resolve-components
  "Walk `hiccup`, replacing every registered tag with its component fn.
   Non-vector, scalar, and nil inputs are returned unchanged."
  [hiccup]
  (walk/postwalk replace-node hiccup))
