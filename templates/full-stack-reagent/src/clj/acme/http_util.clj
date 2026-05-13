(ns acme.http-util)

(defn wants-markdown? [request]
  (let [accept (or (get-in request [:headers "accept"]) "")]
    (boolean (re-find #"(?i)text/(markdown|plain)" accept))))
