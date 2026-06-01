(ns acme.content.core
  (:require [acme.content.hiccup-registry :as registry]
            [acme.content.markdown]
            [acme.http-util]
            [acme.layouts]
            [c3kit.wire.ajax]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [compojure.core]
            [hiccup.core]))

(def ^:private index (atom {}))   ;; {type {permalink post}}

(def ^:private reserved-route-names
  #{"" "api" "ajax" "sandbox" "error" "forgot-password" "google" "apple"
    "recover-password" "redirect" "signout" "user"})

(defn- read-meta [meta-file]
  (try (edn/read-string (slurp meta-file))
       (catch Exception e
         (throw (ex-info (str "Bad meta.edn at " (.getPath meta-file)) {} e)))))

(defn- post-dir? [^java.io.File f]
  (and (.isDirectory f)
       (.exists (io/file f "meta.edn"))
       (.exists (io/file f "content.md"))))

(defn- load-post [^java.io.File post-dir]
  {:permalink (.getName post-dir)
   :meta      (read-meta (io/file post-dir "meta.edn"))
   :markdown  (slurp (io/file post-dir "content.md"))})

(defn- load-type [^java.io.File type-dir]
  (let [type-name (.getName type-dir)]
    (when (reserved-route-names type-name)
      (throw (ex-info (str "content/" type-name " collides with a reserved route name. "
                           "Rename the directory or remove the reserved-name collision.")
                      {:type type-name})))
    [(keyword type-name)
     (->> (.listFiles type-dir)
          (filter post-dir?)
          (map load-post)
          (filter #(-> % :meta :published?))
          (sort-by (comp :published-at :meta) #(compare %2 %1)) ;; newest first
          (map (juxt :permalink identity))
          (into {}))]))

(defn load! []
  (let [root (io/file "content")]
    (reset! index
            (if (.isDirectory root)
              (into {} (->> (.listFiles root)
                            (filter #(.isDirectory ^java.io.File %))
                            (map load-type)))
              {}))))

(defn types [] (keys @index))
(defn posts [type] (some-> (get @index type) vals))
(defn find-post [type permalink] (get-in @index [type permalink]))

(defn- render-post-preview [post]
  (let [hiccup (acme.content.markdown/->hiccup (:markdown post))]
    (hiccup.core/html hiccup)))

(defn- render-list-preview [type posts]
  (hiccup.core/html
   [:section
    [:h1 (clojure.string/capitalize (name type))]
    [:ul
     (for [p posts]
       [:li
        [:a {:href (str "/" (name type) "/" (:permalink p))}
         (-> p :meta :title)]
        (when-let [d (-> p :meta :description)]
          (list " — " d))])]]))

(def wants-markdown? acme.http-util/wants-markdown?)

(defn- yaml-scalar [v]
  (cond
    (nil? v)        "null"
    (string? v)     v
    (keyword? v)    (name v)
    (boolean? v)    (str v)
    (number? v)     (str v)
    (sequential? v) (str "[" (clojure.string/join ", " (map yaml-scalar v)) "]")
    :else           (pr-str v)))

(defn post->markdown [post]
  (let [m (assoc (:meta post) :permalink (:permalink post))]
    (str "---\n"
         (clojure.string/join "\n"
                              (for [[k v] m] (str (name k) ": " (yaml-scalar v))))
         "\n---\n\n"
         (:markdown post))))

(defn posts->markdown-index [type posts]
  (let [header (str "# " (clojure.string/capitalize (name type)) "\n\n")
        lines  (for [p posts]
                 (let [title (-> p :meta :title)
                       desc  (-> p :meta :description)
                       link  (str "/" (name type) "/" (:permalink p))]
                   (if desc
                     (str "- [" title "](" link ") — " desc)
                     (str "- [" title "](" link ")"))))]
    (str header (clojure.string/join "\n" lines) "\n")))

(defn web-post [type]
  (fn [request]
    (let [permalink (-> request :params :permalink)
          post      (find-post type permalink)]
      (cond
        (nil? post)               (acme.layouts/not-found)
        (acme.http-util/wants-markdown? request) {:status  200
                                                  :headers {"Content-Type" "text/markdown; charset=utf-8"}
                                                  :body    (post->markdown post)}
        :else                     (acme.layouts/web-rich-client request
                                                                {:seo/preview    (render-post-preview post)
                                                                 :og/title       (-> post :meta :title)
                                                                 :og/description (-> post :meta :description)
                                                                 :title          (-> post :meta :title)})))))

(defn web-list [type]
  (fn [request]
    (let [ps (posts type)]
      (if (acme.http-util/wants-markdown? request)
        {:status  200
         :headers {"Content-Type" "text/markdown; charset=utf-8"}
         :body    (posts->markdown-index type ps)}
        (acme.layouts/web-rich-client request
                                      {:seo/preview (render-list-preview type ps)
                                       :og/title    (clojure.string/capitalize (name type))
                                       :title       (clojure.string/capitalize (name type))})))))

(defn build-routes []
  (apply compojure.core/routes
         (for [type (types)]
           (compojure.core/routes
            (compojure.core/GET (str "/" (name type))               req ((web-list type) req))
            (compojure.core/GET (str "/" (name type) "/:permalink") req ((web-post type) req))))))

(defn api-fetch-post [request]
  (let [{:keys [type permalink]} (:params request)
        type-kw                  (keyword type)
        post                     (find-post type-kw permalink)]
    (if post
      (c3kit.wire.ajax/ok
       {:meta      (:meta post)
        :permalink (:permalink post)
        :body      (-> post :markdown acme.content.markdown/->hiccup registry/resolve-components)})
      (c3kit.wire.ajax/fail {:error "not-found"} 404))))
