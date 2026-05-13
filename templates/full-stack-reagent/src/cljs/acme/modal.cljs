(ns acme.modal
  (:refer-clojure :exclude [pop!])
  (:require [acme.page :as page]
            [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]
            [reagent.core :as reagent]))

(def state (page/cursor [:modal]))
(def previous (reagent/track #(:modal/previous @state)))
(def subject (reagent/track #(:modal/subject @state)))

(defn install-state! [new-state]
  (log/info "installing modal state: " new-state)
  (reset! state new-state))

(defn install! [section & args]
  (-> (ccc/->options args)
      (assoc :modal/subject section)
      install-state!))

(defn push-state! [new-state] (install-state! (assoc new-state :modal/previous @state)))
(defn push! [section & args]
  (-> (apply hash-map args)
      (assoc :modal/subject section)
      push-state!))

(def pop! #(install-state! @previous))
(defn back-link []
  (when @previous
    [:a#-modal-back.plain-text.modal-back {:on-click pop!} [:h6 "Back"]]))

(defmulti modal-content :modal/subject)

(defn close! []
  (let [on-close (:on-close @state)]
    (reset! state nil)
    (when on-close (on-close))))

(defn- focus-modal! []
  (some-> (or (wjs/element-by-id "-modal")
              (wjs/element-by-id "-modal-overlay"))
          wjs/focus!))

(defn- process-modal-keyup [close e]
  (when (wjs/ESC? e)
    (if (#{"-modal" "-modal-overlay"} (-> e wjs/e-target wjs/node-id))
      (close)
      (focus-modal!))))

(defn overlay [_]
  (reagent/create-class
    {:display-name           "modal-overlay"
     :component-did-mount    (fn [_]
                               (-> js/document wjs/doc-body (wjs/node-add-class "no-scroll"))
                               (focus-modal!))
     :component-will-unmount #(-> js/document wjs/doc-body (wjs/node-remove-class "no-scroll"))
     :reagent-render         (fn [close]
                               [:div#-modal-overlay.modal-overlay {:tab-index "0"
                                                                   :on-key-up (partial process-modal-keyup close)
                                                                   :on-click  close}
                                [:span.close-button-x-white]])}))

(defn modal []
  (when @state
    [:div#-modal.modal-background.centered {:tab-index "0"
                                            :on-key-up (partial process-modal-keyup close!)}
     [overlay close!]

     ^{:key @subject} [modal-content @state]

     (when (:ajax-in-progress @page/state)
       [:div.spinner-overlay ;; TODO - MDM: class missing
        [:div.spinner-medium-grey]])]))

(defmethod modal-content :default [state]
  (log/warn "Unimplemented modal state:")
  (log/warn state)
  [:div#-default-modal.modal.card
   [:div.container
    [:h2 "Default Modal"]
    [:h3 "This should not happen.  How did this happen?"]
    [:pre (pr-str state)]]])

;; MDM - for testing
(defmethod modal-content :modal/hello [_]
  [:div#-hello-modal.modal.card
   [:div.container
    [:h2 "Hello"]]])

(defmethod modal-content :modal/confirm [{:keys [title description on-confirm]}]
  [:div.modal.card
   [:div#-confirm-modal.container
    (back-link)
    [:h3.small-margin-bottom title]
    [:p "Are you should you would like to " description " ?"]
    [:div.button-group.margin-top
     [:button#-confirm-modal-confirm-button.error {:on-click #(do (pop!) (on-confirm))} "Yes"]
     [:button#-confirm-modal-cancel-button.secondary {:on-click pop!} "Cancel"]]]])

(defn confirm! [title description on-confirm]
  (push! :modal/confirm :title title :description description :on-confirm on-confirm))
