(ns acme.sandbox.site.ajax-spinner)

(defn render []

[:div {:id "content"}
 [:header
  [:div {:class "container horizontal-inset-plus-1 inliner space-between"}
   [:a {:href "/"}
    [:img {:src "/images/logos/cc-emblem.png" :class "logo"}]]
   [:div {:class "user-menu-container"}]]]
 [:main
  [:section {:class "home"}
   [:div {:class "container width-300 margin-top-plus-5 margin-bottom-plus-5"}
    [:form
     [:fieldset {:class "small-margin-bottom"}
      [:label "Username"]
      [:input {:type "text" :placeholder "username" :autocomplete "username" :id "-username"}]]
     [:fieldset {:class "small-margin-bottom"}
      [:label "Password"]
      [:input {:type "password" :placeholder "password" :autocomplete "password" :id "-password"}]
      [:p
       [:a {:href "/forgot-password"} "I forgot my password."]]]
     [:fieldset
      [:button {:disabled "true" :id "-signin-button" :class "primary"} "Sign in to Acme"]]]]]
  [:div {:class "site-spinner"}]]]

)
