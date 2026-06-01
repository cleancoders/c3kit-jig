(ns acme.ssr.prerender
  (:require [acme.config :as config]
            ;; @c3kit/feature :content = [acme.content.core :as content]
            [c3kit.apron.log :as log]
            [c3kit.apron.utilc :as utilc]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]))

(defn build-payload []
  (cond-> {:config {:host        config/host
                    :environment config/environment}}
    ;; @c3kit/feature :content {
    :always
    (assoc :content (into {} (for [t (content/types)]
                               [t (into {} (for [p (content/posts t)]
                                             [(:permalink p) p]))])))
    ;; @c3kit/feature :content }
    ))

(defn script-exists? []
  (.exists (io/file "resources/prerender/prerender.js")))

(defn node-installed? []
  (try (zero? (:exit (sh/sh "node" "--version")))
       (catch java.io.IOException _ false)))

(defn run-node! [payload-path]
  (try
    (let [r (sh/sh "node" "resources/prerender/prerender.js" payload-path)]
      (when (seq (:err r)) (log/warn "prerender stderr:" (:err r)))
      (when (seq (:out r)) (log/report (:out r)))
      (:exit r))
    (catch java.io.IOException e
      (log/error "prerender failed: node not found." (.getMessage e))
      1)))

(defn prerender! []
  (cond
    (not (script-exists?))
    (log/warn "Skipping prerender: resources/prerender/prerender.js not found. Run 'clj -M:test:cljs once' first.")

    (not (node-installed?))
    (log/warn "Skipping prerender: node not installed.")

    :else
    (let [payload-file (java.io.File/createTempFile "prerender-payload" ".transit")]
      (try
        (spit payload-file (utilc/->transit (build-payload)))
        (let [exit (run-node! (.getAbsolutePath payload-file))]
          (if (zero? exit)
            (log/report "Prerender complete.")
            (log/warn "Prerender failed; skipping. See logs.")))
        (finally (.delete payload-file))))))
