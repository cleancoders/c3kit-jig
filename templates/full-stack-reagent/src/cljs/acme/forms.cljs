(ns acme.forms
  (:require [acme.modal :as modal]
            [c3kit.apron.schema :as schema]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.js :as wjs]
            [c3kit.wire.util :as wire-util]
            ;; @c3kit/feature :websocket = [c3kit.wire.websocket :as ws]
            ))

(defn focus-and-select [node]
  (when node
    (.focus node)
    (wjs/timeout 0 #(.select node))))

(defn focus-and-select-when
  ([condition] (partial focus-and-select-when condition))
  ([condition node] (when condition (focus-and-select node))))

(defn- field-value [target schema field]
  (case (.-type target)
    "checkbox" (.-checked target)
    "radio" (schema/coerce-value schema field (.-value target)) ;; MDM - can assume valid since we code the value
    (.-value target)))

(defn update-field-value [field value schema ratom]
  (let [state  (assoc @ratom field value)
        errors (schema/conform-message-map schema state)]
    (if errors
      (reset! ratom (assoc state :errors errors))
      (reset! ratom (dissoc state :errors)))))

(defn update-field [schema ratom field e]
  (let [target (.-target e)
        value  (field-value target schema field)]
    (update-field-value field value schema ratom)))

(defn field-error [ratom field]
  (when (:display-errors? @ratom)
    (when-let [error (get-in @ratom [:errors field])]
      [:p.-validation-error.validation-message.error error])))

(defn pokey-error [ratom field]
  (when (:display-errors? @ratom)
    (when-let [error (get-in @ratom [:errors field])]
      [:fieldset.validation-message-container
       [:p.-validation-error.validation-message.error error]])))

(defn capture-errors-handler [ratom success-handler]
  (fn [payload]
    (if-let [errors (:errors payload)]
      (swap! ratom assoc :errors errors :display-errors? true)
      (success-handler payload))))

(defn config
  ([schema ratom url success-handler] (config schema ratom url success-handler nil))
  ([schema ratom url success-handler options]
   {:schema          schema
    :ratom           ratom
    :url             url
    :success-handler success-handler
    :options         options}))

(defn- processing-off [f ratom]
  (fn []
    (swap! ratom dissoc :_processing?)
    (when f (f))))

(defn processing? [ratom] (:_processing? @ratom))

(defn attempt-submit
  ([form-config] (partial attempt-submit form-config))
  ([{:keys [schema ratom url success-handler options]} e] (attempt-submit schema ratom url success-handler options e))
  ([schema ratom url success-handler options] (partial attempt-submit schema ratom url success-handler options))
  ([schema ratom url success-handler options e]
   (wjs/nod e)
   (let [conformed (schema/conform schema @ratom)
         errors    (schema/message-map conformed)]
     (if errors
       (swap! ratom assoc :errors errors :display-errors? true)
       (let [presentable (schema/present! schema conformed)
             ;presentable (assoc presentable :__anti-forgery-token (config/anti-forgery-token))
             options     (update options :after-all processing-off ratom)
             ;; @c3kit/feature :websocket {
             call-fn     (if (keyword? url) ws/call! ajax/post!)]
             ;; @c3kit/feature :websocket }
             ;; @c3kit/feature !:websocket {
             call-fn     ajax/post!]
             ;; @c3kit/feature !:websocket }
         (swap! ratom (fn [v] (-> v (dissoc :errors) (assoc :_processing? true))))
         (call-fn url presentable (capture-errors-handler ratom success-handler) options))))))

(defn modal-attempt-submit
  ([form-config] (partial modal-attempt-submit form-config))
  ([{:keys [schema ratom url success-handler options]} e] (modal-attempt-submit schema ratom url success-handler options e))
  ([schema ratom url success-handler options] (partial modal-attempt-submit schema ratom url success-handler options))
  ([schema ratom url success-handler options e]
   (let [options (assoc options :after-all #(when-not (:errors @ratom) (modal/close!)))]
     (attempt-submit schema ratom url success-handler options e))))

(defn form-field [tag type options field ratom schema]
  [tag (merge {:type       type
               :value      (get @ratom field)
               :on-change  (partial update-field schema ratom field)
               :auto-focus (= field (:focus @ratom))}
              options)])

(defn text-field [options field ratom schema]
  (form-field :input :text options field ratom schema))

(defn date-field [options field ratom schema]
  (form-field :input :date options field ratom schema))

(defn username-field [options field ratom schema]
  (form-field :input :text options field ratom schema))

(defn tel-field [options field ratom schema]
  (form-field :input :tel options field ratom schema))

(defn password-field [options field ratom schema]
  (form-field :input :password options field ratom schema))

(defn number-field
  ([options field {:keys [ratom schema]}] (number-field options field ratom schema))
  ([options field ratom schema] (form-field :input :number options field ratom schema)))

(defn text-area [options field ratom schema]
  (form-field :textarea "" options field ratom schema))

(defn radio-button
  ([options field value {:keys [ratom schema]}] (radio-button options field value ratom schema))
  ([options field value ratom schema]
   [:input (merge {:type      "radio"
                   :name      (str field)
                   :value     (str value)
                   :checked   (= value (get @ratom field))
                   :on-change (partial update-field schema ratom field)}
                  options)]))

(defn fieldset-label [label]
  (when label [:label (if (sequential? label) (wire-util/with-react-keys label) label)]))

(defn field-set
  ([label field-fn options field {:keys [ratom schema]}]
   (field-set label field-fn options field ratom schema))
  ([label field-fn options field ratom schema]
   [:fieldset {:class "small-margin-bottom"}
    (fieldset-label label)
    (field-fn options field ratom schema)
    (field-error ratom field)]))

(defn checkbox-field-set
  ([options field {:keys [ratom schema]} span]
   (checkbox-field-set options field ratom schema span))
  ([options field ratom schema span]
   [:fieldset {:id (:id options)}
    [:label.checkbox.two-lines
     [:input (merge {:type      "checkbox"
                     :on-change (partial update-field schema ratom field)
                     :checked   (if (get @ratom field) true false)}
                    (dissoc options :id))]
     span]
    (field-error ratom field)]))

(defn setup-focus-input [ratom fields]
  (let [field (first (filter #(nil? (get @ratom %)) fields))
        field (or field (first fields))]
    (when field
      (swap! ratom assoc :focus field))))

(defn submit-button [label id {:keys [ratom] :as form-config} can-submit?]
  (let [disabled? (or (processing? ratom) (not can-submit?))]
    [:button.primary {:id       id
                      :class    (when disabled? "disabled")
                      :disabled disabled?
                      :on-click (attempt-submit form-config)}
     (if (processing? ratom) [:span.spinner-white] label)]))
