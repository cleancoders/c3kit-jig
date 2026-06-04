(ns acme.content.page
  (:require [acme.content.hiccup-registry :as registry]
            [acme.page :as page]
            [c3kit.wire.ajax :as ajax]
            [clojure.string :as str]))

(defn install! [type permalink]
  (swap! page/state assoc :content-page {:type type :permalink permalink}))

(defn current-post []
  (let [{:keys [type permalink]} (:content-page @page/state)]
    (get-in @page/state [:content (keyword type) permalink])))

(defn current-list []
  (let [type (:type (:content-page @page/state))]
    (get-in @page/state [:content (keyword type) ::list])))

(defn fetch! [type permalink on-done]
  (ajax/get! (str "/ajax/content/" type "/" permalink) nil
             (fn [post]
               (swap! page/state assoc-in [:content (keyword type) permalink] post)
               (when on-done (on-done post)))
             :on-error (fn [_] (when on-done (on-done nil)))))

(defn fetch-list! [type on-done]
  (ajax/get! (str "/ajax/content/" type) nil
             (fn [{:keys [posts]}]
               (swap! page/state assoc-in [:content (keyword type) ::list] posts)
               (when on-done (on-done posts)))
             :on-error (fn [_] (when on-done (on-done nil)))))

(defmulti render-post (fn [type _post] type))
(defmethod render-post :default [_ post]
  ;; Pipe :body through the registry so custom keyword tags
  ;; (e.g. `[:quote-block {…}]`) swap to their registered reagent fn.
  [:article.content-post
   [:header [:h1 (-> post :meta :title)]
    (when-let [d (-> post :meta :description)] [:p.description d])]
   [:section.body (registry/resolve-components (:body post))]])

(defn render-list [type posts]
  [:section.content-list
   [:h1 (str/capitalize type)]
   [:ul
    (for [{:keys [permalink meta]} posts]
      ^{:key permalink}
      [:li
       [:a {:href (str "/" type "/" permalink)} (:title meta)]
       (when-let [d (:description meta)] [:span.description (str " — " d)])])]])

(defn content-view []
  (let [{:keys [type permalink]} (:content-page @page/state)]
    [:main
     [:div.container.width-750
      (cond
        permalink           (if-let [post (current-post)]
                              (render-post (keyword type) post)
                              [:p "Loading..."])
        (current-list)      (render-list type (current-list))
        :else               [:p "Loading..."])]]))

(defmethod page/render :content/page [_] [content-view])

(defn- enter! [_]
  (let [{:keys [type permalink]} (:content-page @page/state)]
    (cond
      (and type permalink (not (current-post)))       (fetch! type permalink nil)
      (and type (not permalink) (not (current-list))) (fetch-list! type nil))))

;; secretary routes /blog and /blog/:permalink to the same page key, so
;; soft-nav between them is a reenter, not an enter. Both must fetch.
(defmethod page/entering! :content/page [k] (enter! k))
(defmethod page/reentering! :content/page [k] (enter! k))

;; ─── Custom components ───────────────────────────────────────────────
;; Authors write `[:quote-block {:text "…" :attribution "…"}]` on its
;; own line in markdown. The server ships that as raw keyword hiccup;
;; `components` below maps the keyword to a reagent fn so the registry
;; can swap them at render time. Add your own entries here.
;;
;; Registration happens explicitly from `acme.main` via
;; `install-components!` — never as a top-level side effect.

(defn quote-block [{:keys [text attribution]}]
  [:blockquote.quote-block
   [:p text]
   (when attribution [:footer "— " attribution])])

(def components
  {:quote-block quote-block})

(defn install-components! []
  (doseq [[k f] components]
    (registry/register-component! k f)))
