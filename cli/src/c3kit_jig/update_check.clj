(ns c3kit-jig.update-check
  "Detect whether a newer CLI release is available and, when allowed, surface
   it. Pure decision logic plus thin, non-throwing IO glue."
  (:require [babashka.fs :as fs]
            [c3kit-jig.ui :as ui]
            [c3kit-jig.version :as version]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private ttl-ms 86400000)

(def ^:private cache-file
  (str (fs/path (System/getProperty "user.home") ".c3kit" "update-check.edn")))

;; --- pure ---

(defn update-message
  "Notice string when `latest` is strictly newer than `current`, else nil."
  [current latest]
  (when (and latest (neg? (version/semver-compare current latest)))
    (str "A new version of c3kit-jig is available: " current " → " latest
         ". Run `c3kit-jig upgrade` to update.")))

(defn stale?
  "True when `checked-at` is nil or older than `ttl-ms` before `now`."
  [checked-at now ttl-ms]
  (or (nil? checked-at) (> (- now checked-at) ttl-ms)))

(defn parse-cache
  "Parse cache edn; nil on non-map or parse failure."
  [s]
  (try
    (let [m (edn/read-string s)]
      (when (map? m) m))
    (catch Exception _ nil)))

(defn render-cache [m] (pr-str m))

(defn disabled-by-env?
  "True when the opt-out env value is non-blank."
  [env-value]
  (not (str/blank? env-value)))

;; --- io glue (never throws) ---

(defn- now [] (System/currentTimeMillis))

(defn- read-cache []
  (try
    (when (fs/exists? cache-file) (parse-cache (slurp cache-file)))
    (catch Exception _ nil)))

(defn- write-cache! [m]
  (try
    (fs/create-dirs (fs/parent cache-file))
    (spit cache-file (render-cache m))
    (catch Exception _ nil)))

(defn- fetch-tag! []
  (deref (future (try (version/fetch-latest-tag!) (catch Exception _ nil)))
         2000 nil))

(defn- latest-tag []
  (let [cache (read-cache)]
    (if (and cache (not (stale? (:checked-at cache) (now) ttl-ms)))
      (:latest-tag cache)
      (if-let [tag (fetch-tag!)]
        (do (write-cache! {:checked-at (now) :latest-tag tag}) tag)
        (:latest-tag cache)))))

(defn- enabled? []
  (and (not (disabled-by-env? (System/getenv "C3KIT_NO_UPDATE_CHECK")))
       (ui/tty?)))

(defn available-update
  "Return {:current .. :latest ..} when an update is available and checking is
   enabled, else nil. Never throws."
  []
  (when (enabled?)
    (let [current (version/current)
          latest  (latest-tag)]
      (when (update-message current latest)
        {:current current :latest latest}))))

(defn notify!
  "Print an update notice to stderr when an update is available."
  []
  (when-let [{:keys [current latest]} (available-update)]
    (ui/warn (update-message current latest))))
