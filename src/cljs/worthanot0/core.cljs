(ns worthanot0.core
  (:require-macros
   [cljs.core.async.macros :as async :refer (go go-loop)]
   [secretary.core :refer [defroute]])
  (:require 
   [worthanot0.appstate :refer [app]]
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [sablono.core :as html :refer-macros [html]]

   [cljs.core.async :as async :refer (<! >! put! chan)]
   [secretary.core :as secretary]
   [goog.events :as events]
   [taoensso.sente  :as s])
  (:import goog.History
           goog.history.EventType
           goog.history.Html5History))


(enable-console-print!)

(let [{:keys [chsk ch-recv send-fn state]}
      (s/make-channel-socket! "/ws" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state chsk-state))

(defn get-token []
  (str js/window.location.pathname js/window.location.search))

(defn make-history []
  (doto (Html5History.)
    (.setPathPrefix (str js/window.location.protocol
                         "//"
                         js/window.location.host))
    (.setUseFragment false)))

(defn handle-url-change [e]
  ;; log the event object to console for inspection
  (js/console.log e)
  ;; and let's see the token
  (js/console.log (str "Navigating: " (get-token)))
  ;; we are checking if this event is due to user action, such as click a link
  ;; a back button, etc. as opposed to programmatically setting the URL with the API
  (when-not (.-isNavigation e)
    ;; in this case, we're setting it
    (js/console.log "Token set programmatically")
    ;; let's scroll to the top to simulate a navigation
    (js/window.scrollTo 0 0))
  ;; dispatch on the token
  (secretary/dispatch! (get-token)))

(defonce history 
  (doto (make-history)
    (goog.events/listen EventType.NAVIGATE
                        ;; wrap in a fn to allow live reloading
                        #(handle-url-change %))
    (.setEnabled true)))

(defn set-html! [el content]
  (aset el "innerHTML" content))

(defn nav! [token]
  (.setToken history token))

(secretary/set-config! :prefix "#") 

(defmulti handle-event
  "Handle events based on the event ID."
  (fn [[ev-id ev-arg] app owner] ev-id))

(defmethod handle-event :default
  [event app owner]
  (.log js/console event)
  #_(println "UNKNOWN EVENT" event)) 

(defmethod handle-event :session/state
  [[_ state] app owner]
  (om/set-state! owner :session/state state))

(defmethod handle-event :auth/fail
  [_ app owner]
  (om/update! app [:username] nil)
  (om/update! app [:login/fail] "Invalid credentials"))

(defmethod handle-event :auth/success
  [[_ token] app owner]
  (om/update! app [:token] (:token token))
  (om/set-state! owner :session/state :secure)) 
 
(defmethod handle-event :register/fail
  [[_ error-msgs] app owner]
  (om/update! app [:username] nil)
  (om/update! app [:register/fail] error-msgs))

(defmethod handle-event :register/success
  [[_ token] app owner]
  (om/update! app [:token] token)
  (om/set-state! owner :session/state :secure))

(defn test-session
  "Ping the server to update the session state." 
  [app owner]
  (println "send token : ") 
  (println (get-in @app [:token]))
  (chsk-send! [:session/status [(get-in @app [:token])]]))

(defn field-change
  "Generic input field updater. Keeps state in sync with input."
  [e owner field]
  (let [value (.. e -target -value)]
    (om/set-state! owner field value)))

(defn do-logout
  [ev app owner]
  (nav! "/")
  (om/set-state! owner :session/state :open)
  (om/update! app [:token] nil)
  (om/update! app [:username] nil))

(defn go-upload
  [ev app owner]
  (nav! "/#/upload"))

(defn profile-form
  [app owner] 
  (reify
    om/IRenderState
    (render-state [_ state]  
      (html [:div
             [:div (str "Logged in as : " (get-in @app [:username]))]
             [:div 
              [:form {:on-submit #(go-upload % app owner)}
               [:input {:class "button-primary" :value "Upload" :type "submit"}]]]
             [:div 
              [:form {:on-submit #(do-logout % app owner)}
               [:input {:class "button-primary" :value "Logout" :type "submit"}]]]]))))

(defn attempt-login
  "Handle the login event - send credentials to the server."
  [ev app owner]
  (let [username (-> (om/get-node owner "username") .-value)
        password (-> (om/get-node owner "password") .-value)]
    (om/update! app [:username] username)
    (om/update! app [:login/fail] nil)
    (chsk-send! [:session/auth [username password]]))
  ;; suppress the form submit:
  false)

(defn attempt-register
  "Handle the login event - send credentials to the server."
  [ev app owner]
  (let [username (-> (om/get-node owner "username-register") .-value)
        password (-> (om/get-node owner "password-register") .-value)
        email (-> (om/get-node owner "email-register") .-value)]
    (om/update! app [:username] username)
    (om/update! app [:register/fail] nil)
    (chsk-send! [:session/register [username password email]]))
  ;; suppress the form submit:
  false)

(defn login-form  
  [app owner]
  (reify 
    om/IInitState
    (init-state [this]
      {:username nil
       :password nil})
    om/IRenderState
    (render-state [_ {:keys [username password username-register 
                             password-register email-register]}]
      (html [:div 
             (when-let [error (:login/fail app)]
               [:div {:style #js {:color "red"}} error]) 
             [:form {:on-submit #(attempt-login % app owner)}
              [:div
               [:div "Username"]
               [:input {:ref "username" :type "text" :value username
                        :on-change #(field-change % owner :username)}]]
              [:div
               [:div "Password"]
               [:input {:ref "password" :type "password" :value password
                        :on-change #(field-change % owner :password)}]]
              [:div
               [:input {:class "button-primary" :type "submit" :value "Login"}]]]
             [:div]
             (when-let [error (:register/fail app)]
               [:div {:style #js {:color "red"}} error])
             [:form {:on-submit #(attempt-register % app owner)}
              [:div
               [:div "Username"]
               [:input {:ref "username-register" :type "text" :value username-register
                        :on-change #(field-change % owner :username-register)}]]
              [:div
               [:div "Password"]
               [:input {:ref "password-register" :type "password" :value password-register
                        :on-change #(field-change % owner :password-register)}]]
              [:div
               [:div "Email"]
               [:input {:ref "email-register" :type "email" :value email-register
                        :on-change #(field-change % owner :email-register)}]]
              [:div
               [:input {:class "button-primary" :type "submit" :value "Register"}]]]]))))

(defn event-loop
  [app owner]
  (go (loop [[op arg] (:event (<! ch-chsk))]
        (.log js/console (str "op: " op "\n" "arg: " arg))
        (case op
          :chsk/recv (handle-event arg app owner)
          ;; we ignore other Sente events
          (test-session app owner))
        (recur (:event (<! ch-chsk))))))

(defn sidebar
  [app owner]
  (reify
    om/IInitState
    (init-state [this]
      {:session/state :unknown})
    om/IWillMount
    (will-mount [this]
      (event-loop app owner))
    om/IRenderState
    (render-state [this state]
      (case (:session/state state)
        :open
        (om/build login-form app {})
        :secure
        (om/build profile-form app {})
        :unknown
        (dom/div nil "Loading...")))))

;; (defn window
;;   [app owner]
;;   (reify
;;     om/IInitState))
;; (om/root window
;;          app
;;          {:target (. js/document (getElementById "window"))})

(def window (. js/document (getElementById "window")))

(defroute home-path "/" []
  (set-html! window "<h1>OMG! YOU'RE HOME!</h1>"))

(defroute user-path "/upload" []
  (set-html! window "<h1>UPLOAD!</h1>"))

(defroute jackpot-path "/777" []
  (set-html! window "<h1>YOU HIT THE JACKPOT!</h1>"))

(defroute "*" []
  (set-html! window "<h1>LOL! YOU LOST!</h1>"))

(om/root sidebar
         app
         {:target (. js/document (getElementById "sidebar"))})
