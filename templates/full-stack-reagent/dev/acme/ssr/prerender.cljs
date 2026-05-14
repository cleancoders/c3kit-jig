(ns acme.ssr.prerender
  (:require [acme.config :as config]
            [acme.init :as init]
            [acme.page :as page]
            [acme.ssr.prerender-pages]
            [c3kit.apron.utilc :as utilc]
            [reagent.core :as r]
            [reagent.dom.server :as rds]))

(defn- page-key->basename [page-key]
  (if (= :home page-key) "index" (name page-key)))

(defn- render-page [page-key]
  (try
    (rds/render-to-string [page/render page-key])
    (catch :default e
      (js/console.error (str "Failed to render " page-key ": " e))
      nil)))

(defn- write-file [path content]
  (.writeFileSync (js/require "fs") path content "utf-8"))

(defn- ensure-dir [path]
  (let [fs (js/require "fs")]
    (when-not (.existsSync fs path)
      (.mkdirSync fs path #js {:recursive true}))))

(defn- stub-browser-apis! []
  (when-not (exists? js/hljs)
    (set! js/hljs #js {:highlightElement (fn [_])})))

(defn- html->markdown [html]
  (let [td-ctor (js/require "turndown")
        td      (new td-ctor #js {:headingStyle     "atx"
                                  :codeBlockStyle   "fenced"
                                  :bulletListMarker "-"})]
    (.turndown td html)))

(defn- write-page! [output-dir page-key html]
  (let [base     (page-key->basename page-key)
        html-out (str output-dir "/" base ".html")
        md-out   (str output-dir "/" base ".md")]
    (write-file html-out html)
    (try (write-file md-out (html->markdown html))
         (catch :default e
           (js/console.warn (str "Markdown conversion failed for " page-key ": " e))))))

(defn -main [& args]
  (let [payload-path (first args)
        fs           (js/require "fs")
        payload      (utilc/<-transit (.readFileSync fs payload-path "utf-8"))
        output-dir   (or (second args) "resources/prerendered")]
    (r/set-default-compiler! (r/create-compiler {:function-components true}))
    (init/install-legend!)
    (init/install-reagent-db-atom!)
    (init/configure-api!)
    (config/install! (:config payload))
    (when-let [content-by-type (:content payload)]
      (swap! page/state assoc :content content-by-type))
    (stub-browser-apis!)
    (ensure-dir output-dir)
    (let [pages (->> (keys (methods page/prerender?))
                     (filter page/prerender?)
                     (remove #{:default}))]
      (js/console.log (str "Prerendering " (count pages) " pages..."))
      (doseq [page-key pages]
        (js/console.log (str "  Rendering " page-key "..."))
        (when-let [html (render-page page-key)]
          (write-page! output-dir page-key html)
          (js/console.log (str "  Wrote " (page-key->basename page-key) ".html + .md")))))))

(set! *main-cli-fn* -main)
