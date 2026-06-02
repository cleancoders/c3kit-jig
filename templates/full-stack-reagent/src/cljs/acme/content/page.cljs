(ns acme.content.page
  (:require [acme.content.hiccup-registry :as registry]
            [acme.page :as page]
            [c3kit.wire.ajax :as ajax]))

(defn install! [type permalink]
  (swap! page/state assoc :content-page {:type type :permalink permalink}))

(defn current-post []
  (let [{:keys [type permalink]} (:content-page @page/state)]
    (get-in @page/state [:content (keyword type) permalink])))

(defn fetch! [type permalink on-done]
  (ajax/get! (str "/api/v1/content/" type "/" permalink) nil
             (fn [post]
               (swap! page/state assoc-in [:content (keyword type) permalink] post)
               (when on-done (on-done post)))
             :on-error (fn [_] (when on-done (on-done nil)))))

(defmulti render-post (fn [type _post] type))
(defmethod render-post :default [_ post]
  ;; Pipe :body through the registry so custom keyword tags
  ;; (e.g. `[:quote-block {…}]`) swap to their registered reagent fn.
  [:article.content-post
   [:header [:h1 (-> post :meta :title)]
    (when-let [d (-> post :meta :description)] [:p.description d])]
   [:section.body (registry/resolve-components (:body post))]])

(defn content-view []
  (if-let [post (current-post)]
    (render-post (keyword (get-in @page/state [:content-page :type])) post)
    [:main [:p "Loading..."]]))

(defmethod page/render :content/page [_] [content-view])

(defmethod page/entering! :content/page [_]
  (let [{:keys [type permalink]} (:content-page @page/state)]
    (when (and type permalink (not (current-post)))
      (fetch! type permalink nil))))

;; ─── Example custom component ────────────────────────────────────────
;; Authors write `[:quote-block {:text "…" :attribution "…"}]` on its
;; own line in markdown. The server ships that as raw keyword hiccup;
;; the registry below swaps it for this reagent fn at render time.
;; Add your own components the same way and require this namespace
;; (or the ns that registers them) from `acme.main`.

(defn quote-block [{:keys [text attribution]}]
  [:blockquote.quote-block
   [:p text]
   (when attribution [:footer "— " attribution])])

(registry/register-component! :quote-block quote-block)
