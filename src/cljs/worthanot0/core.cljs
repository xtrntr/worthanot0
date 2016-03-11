(ns worthanot0.core
  (:require-macros
   [cljs.core.async.macros :as async :refer (go go-loop)])
  (:require 
   [worthanot0.appstate :refer [app]]
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [sablono.core :as html :refer-macros [html]]
   [cljs.core.async :as async :refer (<! >! put! chan)]
   [taoensso.sente  :as s]))

(enable-console-print!)

(let [{:keys [chsk ch-recv send-fn state]}
      (s/make-channel-socket! "/ws" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state chsk-state))

(defmulti handle-event
  "Handle events based on the event ID."
  (fn [[ev-id ev-arg] app owner] ev-id))

(defmethod handle-event :test/reply
  [[_ msg] app owner]
  (.log js/console msg)
  (om/update! app :data/text msg))

(defmethod handle-event :default
  [event app owner]
  (.log js/console event)
  #_(println "UNKNOWN EVENT" event)) 

(defmethod handle-event :session/state
  [[_ state] app owner]
  (om/set-state! owner :session/state state))

(defmethod handle-event :auth/fail
  [_ app owner]
  (.log js/console "auth fail")
  (om/update! app [:username] "")
  (om/update! app [:notify/error] "Invalid credentials"))

(defmethod handle-event :auth/success
  [[_ token] app owner]
  (om/update! app :token token)
  (.log js/console (get-in @app [:username]))
  (om/set-state! owner :session/state :secure))
 
(defn test-session
  "Ping the server to update the session state."
  [owner]
  (chsk-send! [:session/status]))

(defn field-change
  "Generic input field updater. Keeps state in sync with input."
  [e owner field]
  (let [value (.. e -target -value)]
    (om/set-state! owner field value)))

(defn profile-form
  [app owner]
  (reify
    om/IRenderState
    (render-state [_ state]  
      (html [:div
             [:div (str "Logged in as : " (get-in @app [:username]))]]))))

(defn attempt-login
  "Handle the login event - send credentials to the server."
  [ev app owner]
  (let [username (-> (om/get-node owner "username") .-value)
        password (-> (om/get-node owner "password") .-value)]
    (om/update! app :username username)
    (om/update! app [:notify/error] nil)
    (chsk-send! [:session/auth [username password]]))
  ;; suppress the form submit:
  false)

(defn login-form  
  [app owner]
  (reify 
    om/IRenderState
    (render-state [_ {:keys [username password]}]
      (html [:div 
             (when-let [error (:notify/error app)]
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
               [:input {:class "button-primary" :type "submit" :value "Login"}]]]]))))

(defn event-loop
  [app owner]
  (go (loop [[op arg] (:event (<! ch-chsk))]
        (.log js/console (str "op: " op "\n" "arg: " arg))
        (case op
          :chsk/recv (handle-event arg app owner)
          ;; we ignore other Sente events
          (test-session owner))
        (recur (:event (<! ch-chsk))))))

(defn application
  [app owner]
  (reify
    om/IInitState
    (init-state [this]
      {:session/state :unknown
       :username ""
       :password ""})
    om/IWillMount
    (will-mount [this]
      (event-loop app owner))
    om/IRenderState
    (render-state [this state]
      (case (:session/state state)
        :open
        (om/build login-form app {})
        :secure
        (om/build profile-form app {:init-state {:username (:username state)
                                                 :password (:password state)}})
        :unknown
        (dom/div nil "Loading...")))))

(om/root application
         app
         {:target (. js/document (getElementById "sidebar"))})

