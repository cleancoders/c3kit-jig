(ns acme.sandbox.site.not-found)

(defn render []
  [:div#content
   [:header
    [:div.container.inliner.space-between
     [:a {:href "/"} [:img.logo {:src "/images/logos/cc-emblem.png"}]]]]
   [:main
    [:section
     [:div.container.width-300
      [:h2.margin-bottom-0 "Not Found (404)"]
      [:img.margin-bottom-plus-2 {:src "/images/not-found.jpg"}]
      [:div
       [:p "Lost in the code scene,"]
       [:p "Cursor blinking on the screen,"]
       [:p "She wished her code clean."]]]]]])

