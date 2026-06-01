(ns acme.content.markdown
  "Server-side markdown → hiccup. Thin wrapper around `nextjournal/markdown`
   so the content pipeline can ship hiccup over AJAX and render previews
   server-side via `hiccup2.core/html`.

   The hiccup shape follows nj/markdown's default renderer map:
   - headings carry a slugified `:id` attribute,
   - tight lists do not wrap items in `[:p ...]`,
   - ordered lists carry a `:start` attribute,
   - fenced code uses the `:code.language-X` class shorthand,
   - GFM table header cells are `:th`, body cells `:td`.

   We add two custom renderers — `:html-inline` and `:html-block` — that
   pass raw HTML text through verbatim instead of nj/markdown's default
   `Unknown type` error span."
  (:require [clojure.string :as str]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md-transform]))

(defn- html-passthrough [_ctx node]
  (-> node :content first :text))

(def hiccup-renderers
  (assoc md-transform/default-hiccup-renderers
         :html-inline html-passthrough
         :html-block  html-passthrough))

(defn ->hiccup
  "Parse a markdown string to hiccup. Returns `nil` for blank/nil input.

   nj/markdown always wraps top-level output in `[:div ...]`. When there
   is exactly one child element we unwrap it and return that element
   directly, matching the historical single-element contract of this
   namespace. Multi-element output keeps the `:div` wrapper so it
   stays render-safe."
  [md-string]
  (when-not (str/blank? md-string)
    (let [tree   (md/parse md-string)
          result (md-transform/->hiccup hiccup-renderers tree)
          kids   (rest result)]
      (cond
        (empty? kids)      nil
        (= 1 (count kids)) (first kids)
        :else              result))))
