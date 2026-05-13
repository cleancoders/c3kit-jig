(ns c3kit-create.args
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]))

(def ^:private CLI-OPTIONS
  [["-t" "--template ID"       "Template id (skip template prompt)"]
   [nil  "--template-ref REF"  "Git ref/tag/branch for template fetch"]
   [nil  "--template-dir PATH" "Use local templates dir instead of fetching"]
   ["-y" "--yes"               "Accept all feature defaults, non-interactive"]
   [nil  "--install"           "Run `clj -P` and `npm install` after scaffold"]
   [nil  "--no-git"            "Skip `git init` and initial commit"
    :id :git? :default true :parse-fn (constantly false)]
   [nil  "--debug"             "Print full stack traces on error"]
   ["-h" "--help"              "Show this help"]
   [nil  "--version"           "Print CLI version"]
   [nil  "--list"              "List available templates"]
   [nil  "--upgrade"           "Download latest CLI release"]])

(defn help []
  (str "c3kit-create — scaffold a new Clojure project from a c3kit template\n\n"
       "USAGE\n"
       "  c3kit-create [<name>] [options]\n"
       "  c3kit-create --list\n"
       "  c3kit-create --version\n"
       "  c3kit-create --upgrade\n\n"
       "OPTIONS\n"
       (:summary (cli/parse-opts [] CLI-OPTIONS))))

(defn- env-default [opts key env]
  (if (contains? opts key) opts
      (if-let [v (System/getenv env)]
        (assoc opts key v)
        opts)))

(defn- detect-action [opts _arguments]
  (cond
    (:help opts)    :help
    (:version opts) :version
    (:list opts)    :list
    (:upgrade opts) :upgrade
    :else           :scaffold))

(defn parse [argv]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts argv CLI-OPTIONS)
        options' (cond-> options
                   (first arguments) (assoc :name (first arguments))
                   true              (env-default :template-dir "C3KIT_TEMPLATES"))]
    (cond
      (seq errors)
      {:action :error :error (str/join "\n" errors) :summary summary}

      :else
      {:action  (detect-action options' arguments)
       :options options'
       :summary summary})))
