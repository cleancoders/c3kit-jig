(ns c3kit-jig.args
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]))

(def ^:private TRUTHY  #{"true"  "yes" "y" "1" "on"})
(def ^:private FALSY   #{"false" "no"  "n" "0" "off"})

(defn- parse-feature [s]
  (let [[k v] (str/split (or s "") #"=" 2)]
    (when (or (str/blank? k) (nil? v))
      (throw (ex-info (str "expected id=bool, got: " (pr-str s)) {})))
    (let [vlow (str/lower-case v)]
      (cond
        (TRUTHY vlow) [(keyword k) true]
        (FALSY  vlow) [(keyword k) false]
        :else (throw (ex-info (str "feature value must be bool, got: " (pr-str v)) {}))))))

(def ^:private CREATE-OPTIONS
  [["-t" "--template ID"        "Template id (skip template prompt)"]
   [nil  "--template-ref REF"   "Git ref/tag/branch for template fetch"]
   [nil  "--template-dir PATH"  "Use local templates dir instead of fetching"]
   ["-y" "--yes"                "Accept all feature defaults, non-interactive"]
   [nil  "--install"            "Run `clj -P` and `npm install` after scaffold"]
   [nil  "--no-git"             "Skip `git init` and initial commit"
    :id :git? :default true :parse-fn (constantly false)]
   [nil  "--db ID"              "Database id (skip db prompt)"
    :parse-fn keyword]
   [nil  "--feature K=V"        "Override feature default (repeatable)"
    :id       :feature
    :default  {}
    :parse-fn parse-feature
    :assoc-fn (fn [m k [fk fv]] (update m k assoc fk fv))]
   [nil  "--debug"              "Print full stack traces on error"]
   [nil  "--target-parent PATH" "(internal) override CWD for scaffold target"]
   ["-h" "--help"               "Show this help"]])

(def ^:private SUBCOMMANDS
  {"create"  :scaffold
   "list"    :list
   "upgrade" :upgrade
   "version" :version
   "help"    :help})

(defn help []
  (str "c3kit-jig — scaffold and manage c3kit Clojure projects\n\n"
       "USAGE\n"
       "  c3kit-jig <subcommand> [options]\n\n"
       "SUBCOMMANDS\n"
       "  create [<name>] [options]   Scaffold a new project from a template\n"
       "  list                        List available templates\n"
       "  upgrade                     Download latest CLI release\n"
       "  version                     Print CLI version\n"
       "  help                        Show this help\n\n"
       "CREATE OPTIONS\n"
       (:summary (cli/parse-opts [] CREATE-OPTIONS))))

(defn- env-default [opts key env]
  (if (contains? opts key) opts
      (if-let [v (System/getenv env)]
        (assoc opts key v)
        opts)))

(defn- parse-create [rest-argv]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts rest-argv CREATE-OPTIONS)
        options' (cond-> options
                   (first arguments) (assoc :name (first arguments))
                   true              (env-default :template-dir "C3KIT_TEMPLATES"))]
    (cond
      (:help options')
      {:action :help :summary summary}

      (seq errors)
      {:action :error :error (str/join "\n" errors) :summary summary}

      :else
      {:action  :scaffold
       :options options'
       :summary summary})))

(defn parse [argv]
  (let [[head & rest-argv] argv]
    (cond
      (nil? head)
      {:action :help}

      (= head "--help")
      {:action :help}

      (= head "--version")
      {:action :version}

      (contains? SUBCOMMANDS head)
      (let [action (SUBCOMMANDS head)]
        (case action
          :scaffold (parse-create rest-argv)
          {:action action}))

      :else
      {:action :error :error (str "Unknown subcommand: " head)})))
