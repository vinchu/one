(ns ^{:doc "Render the views for the application."}
  one.sample.view
  (:use [domina :only (xpath set-html! set-styles! styles by-id set-style!
                       value set-value! set-text!)])
  (:require-macros [one.sample.snippets :as snippets])
  (:require [clojure.browser.event :as event]
            [one.dispatch :as dispatch]
            [one.sample.animation :as fx]))

(def ^{:doc "A map which contains chunks of HTML which may be used
  when rendering views."}
  snippets (snippets/snippets))

(defmulti render-button
  "Render the submit button based on the current state of the
  form. The button is disabled while the user is editing the form and
  becomes enabled when the form is complete."
  identity)

(defmethod render-button :default [_])

(defmethod render-button [:finished :editing] [_]
  (fx/disable-button "greet-button"))

(defmethod render-button [:editing :finished] [_]
  (fx/enable-button "greet-button"))

(defmulti render-form-field
  "Render a form field based on the current state transition. Form
  fields are validated as soon as they lose focus. There are six
  transitions and each one has its own animation."
  :transition)

(defmethod render-form-field :default [_])

(defn- label-xpath
  "Accepts an element id for an input field and return the xpath
  string to the label for that field."
  [id]
  (str "//label[@id='" id "-label']/span"))

(defmethod render-form-field [:empty :editing] [{:keys [id]}]
  (fx/label-move-up (label-xpath id)))

(defmethod render-form-field [:editing :empty] [{:keys [id]}]
  (fx/label-move-down (label-xpath id)))

(defmethod render-form-field [:editing :valid] [{:keys [id]}]
  (fx/label-fade-out (label-xpath id)))

(defmethod render-form-field [:valid :editing] [{:keys [id]}]
  (fx/play (label-xpath id) fx/fade-in))

(defmethod render-form-field [:editing :error] [{:keys [id error]}]
  (let [error-element (by-id (str id "-error"))]
    (set-style! error-element "opacity" "0")
    (set-html! error-element error)
    (fx/play error-element fx/fade-in)))

(defmethod render-form-field [:error :editing] [{:keys [id]}]
  (let [error-element (by-id (str id "-error"))]
    (fx/play error-element (assoc fx/fade-out :time 1000))))

(defn- add-input-event-listeners
  "Accepts a field-id and creates listeners for blur and focus events which will then fire
  :field-changed and :editing-field events."
  [field-id]
  (let [field (by-id field-id)]
    (event/listen field
                  "blur"
                  #(dispatch/fire [:field-changed field-id] (value field)))
    (event/listen field
                  "focus"
                  #(dispatch/fire [:editing-field field-id]))))

(defmulti render
  "Accepts a map which represents the current state of the application
  and renders a view based on the value of the :state key."
  :state)

(defmethod render :init [_]
  (fx/initialize-views (:form snippets) (:greeting snippets))
  (add-input-event-listeners "name-input")
  (fx/disable-button "greet-button")
  (event/listen (by-id "greet-button")
                "click"
                #(dispatch/fire :greeting
                                {:name (value (by-id "name-input"))})))

(defmethod render :form [{:keys [state error name]}]
  (fx/show-form)
  (set-value! (by-id "name-input") "")
  (dispatch/fire [:field-changed "name-input"] ""))

(defmethod render :greeting [{:keys [state name exists]}]
  (set-text! (by-id "name") (if exists (str " again " name) name))
  (fx/show-greeting))

(dispatch/react-to #{:state-change} (fn [_ m] (render m)))

(defn- form-fields-status
  "Given a map of old and new form states, generate a map with :id, :transition and
  :error keys which can be passed to render-form-field."
  [m]
  (map #(hash-map :id %
                  :transition [(or (-> m :old :fields % :status) :empty)
                               (-> m :new :fields % :status)]
                  :error (-> m :new :fields % :error))
       (keys (-> m :new :fields))))

(dispatch/react-to #{:form-change}
                   (fn [_ m]
                     (doseq [s (form-fields-status m)]
                       (render-form-field s))
                     (render-button [(-> m :old :status)
                                     (-> m :new :status)] )))
