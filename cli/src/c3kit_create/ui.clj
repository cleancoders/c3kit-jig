(ns c3kit-create.ui)

(def ^:dynamic *color?* true)

(def ^:private CODES
  {:reset  "[0m"
   :red    "[31m"
   :green  "[32m"
   :yellow "[33m"
   :blue   "[34m"
   :gray   "[90m"
   :bold   "[1m"})

(defn tty?
  "True when stdout is connected to a terminal."
  []
  (some? (System/console)))

(defn colorize [color s color?]
  (if color?
    (str (CODES color) s (CODES :reset))
    (str s)))

(defn- emit [stream prefix-color prefix-glyph msg]
  (binding [*out* stream]
    (println (str (colorize prefix-color prefix-glyph *color?*) " " msg))))

(defn step [msg] (emit *out* :blue   "▸" msg))
(defn ok   [msg] (emit *out* :green  "✓" msg))
(defn warn [msg] (emit *err* :yellow "⚠" msg))
(defn fail [msg] (emit *err* :red    "✗" msg))
(defn info [msg] (binding [*out* *out*] (println msg)))

(defn friendly-error
  "Wrap an Exception with a user-friendly one-liner. Original retained as cause."
  [msg ^Throwable cause]
  (ex-info msg {:friendly? true :cause cause} cause))
