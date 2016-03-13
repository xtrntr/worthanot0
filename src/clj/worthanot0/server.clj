(ns worthanot0.server
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join]]
            [clojure.core.async :as async :refer [<! <!! chan go thread]]
            [clojure.core.cache :as cache]

            [buddy.sign.jwe :as jwe]
            [buddy.core.keys :as keys]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            
            [worthanot0.db.core :as db]
            [worthanot0.security :as sec]

            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route :refer [resources]]
            [compojure.handler :as handler]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.session.memory :refer [memory-store]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.util.response :refer [resource-response file-response redirect response content-type]]

            [net.cgrand.enlive-html :as html :refer [deftemplate]]

            [bouncer.core :as b]
            [bouncer.validators :as v]
            [cheshire.core :as json]
            [clj-uuid :as uuid]
            [environ.core :refer [env]]
            [aws.sdk.s3 :as s3]
            [taoensso.sente :as s]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            )
  (:gen-class)) 
  
;; configs
(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (s/make-channel-socket! sente-web-server-adapter {})]
  (def ring-ajax-post   ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk          ch-recv)
  (def chsk-send!       send-fn)
  (def connected-uids   connected-uids) ; Watchable, read-only atom
  )

(def koochy (atom {}))

(def domain-name "http://s3-ap-southeast-1.amazonaws.com/")
(def bucket "worthanot-listings")
(def aws-zone "ap-southeast-1")
(def access-key "AKIAJ7ZW2CNCTPVYPNIA")
(def secret-key "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")
(def cred {:access-key access-key :secret-key secret-key})
(def pubkey (keys/public-key "public.pem"))
(def privkey (keys/private-key "private.pem" "12345"))

(def index (io/resource "templates/base.html"))

(def session-map (atom (cache/ttl-cache-factory {} :ttl (* 5 60 1000))))

(defn unique-id
  []
  (rand-int 10000))

(defn keep-alive
  [uid]
  (when-let [token (get @session-map uid)]
    (swap! session-map assoc uid token)))

(defn add-token
  [uid token]
  (swap! session-map assoc uid token))

(defn get-token
  [uid]
  (let [token (get @session-map uid)]
    (println "get-token" uid token (java.util.Date.))
    token))

;;return true if no user found, return false if user found
;;when custom validator return false, trigger check
(defn duplicate-user?
  [user]
  (not (not-empty (db/get-user {:username user}))))

(defn duplicate-email?
  [email]
  (if (= email "") ;no email input
    false
    (not (not-empty (db/get-email {:email email})))))

(defn make-token-pair!
  [user]
  {:token {:auth-token (jwe/encrypt {:user user} pubkey {:alg :rsa-oaep :enc :a128cbc-hs256})
           :refresh-token nil 
           ;;https://rundis.github.io/blog/2015/buddy_auth_part3.html
           ;;use this in the future to store refresh a refresh token.
           }})

(defn session-uid
  "Convenient to extract the UID that Sente needs from the request."
  [req]
  (get-in req [:session :uid]))

(defn token-expire?
  [token]
  ;;extract from refresh token later.
  (get-in @token [:token :auth-token]))

(defn session-status
  "Tell the server what state this user's session is in."
  [token req]
  (when-let [uid (session-uid req)]
    (chsk-send! uid [:session/state (if token :secure :open)])))

(defmulti handle-event
  "Handle events based on the event ID."
  (fn [[ev-id ev-arg] ring-req] ev-id))

(defmethod handle-event :session/status
  [[_ [token]] req]
  (println (str "auth token : " token))
  (session-status token req))

;; Reply with authentication failure or success. For a successful authentication, remember the login.
(defmethod handle-event :session/auth
  [[_ [username password]] req]
  (when-let [uid (get-in req [:session :uid])]
    (let [;;kind of circular reasoning here. clear up later. we check for db entry before
          ;;doing any kind of validating when it should not be that way
          db-entry (when-let [entry (not-empty (db/get-user {:username username}))]
                     (nth entry 0))
          db-hashed-password (:password db-entry)
          user-exists? (fn [user] db-entry) 
          password-match? (fn [pass] (sec/check-password db-hashed-password pass))
          validation-vals (-> {:username username
                               :password password}
                              (b/validate :username [v/required [v/max-count 30]]
                                          :password [v/required [v/max-count 30]]) 
                              second
                              (b/validate :username [[user-exists? :message "no such user"]])
                              second 
                              (b/validate :password [[password-match? :message "wrong password"]])) 
          valid? (not (first validation-vals))
          error-map (::b/errors (second validation-vals))
          error-msgs (str "Error: " (join ", " (-> error-map 
                                                   vals
                                                   flatten)))
          token (make-token-pair! username)]
      (if valid?
        (chsk-send! uid [:auth/success token])
        (chsk-send! uid [:auth/fail])
        ))))

(defmethod handle-event :session/register
  [[_ [username password email]] req]
  (when-let [uid (get-in req [:session :uid])]
    (let [params {:email email
                  :password password
                  :username username}
          validation-vals (-> params 
                              (b/validate :username [v/required [v/max-count 30]]
                                          :password [v/required [v/max-count 30]]
                                          ;;if email field is not empty then check whether it's a valid email
                                          :email [[v/email :pre (comp not-empty :email)]])
                                        ;second
                              (b/validate :email [[duplicate-email? :message "email in use already"]])
                              second
                              (b/validate :username [[duplicate-user? :message "username in use already"]])
                              second)
          valid? (not (first validation-vals))
          error-map (::b/errors (second validation-vals))
          error-msgs (str "Error: " (join ". " (-> error-map vals flatten))) 
          token (make-token-pair! username)]
      (println (str "\n\n\n" validation-vals "\n\n\n"))
      (println (str "\n\n\n" (first validation-vals) "\n\n\n"))
      (println (str "\n\n\n" (not validation-vals) "\n\n\n"))
      (if valid?
        (do (db/create-user! {:email email
                              :password (sec/encrypt-password password)
                              :username username})
            (chsk-send! uid [:register/success token]))
        (chsk-send! uid [:register/fail error-msgs])))))

(defmethod handle-event :chsk/ws-ping
  [_ req]
  (session-status [] req))

(defmethod handle-event :default
  [event req]
  (session-status [] req))

(html/deftemplate unauth index
  [title]
  [:h1] (html/content title))
  
(html/defsnippet img-snippet index
  [[:div.content]]  
  [img-url] 
  [:div.content [:img]] (html/set-attr :src img-url))  
   
(defn unauth-page
  [title req]
  {:status 200 :session {:uid (uuid/v4)} :body (unauth title)}) 

(defn get-img-url
  [img-id]
  (str domain-name "/" bucket "/" img-id ".jpg")) 

(defn is-authenticated? 
  [{session :session :as req}]
  (let [user (:user (jwe/decrypt (get-in session [:token :auth-token]) privkey {:alg :rsa-oaep :enc :a128cbc-hs256}))
        auth-user? (not (not-empty (db/get-user {:username user})))]
    auth-user?))

(defn upload-to-amazon
  [req]
  (let [img-file (get-in req [:multipart-params "file" :tempfile])
        listing-name (get-in req [:multipart-params "listing"])
        user (:user (jwe/decrypt (get-in req [:session :token :auth-token]) privkey {:alg :rsa-oaep :enc :a128cbc-hs256}))
        user-uuid (:user_id (nth (db/get-user {:username user}) 0))
        listing-uuid (uuid/v4)
        image-uuid (uuid/v4)  
        string (new String)]
    (db/create-listing! {:listing_id listing-uuid :user_id user-uuid})
    (db/create-image! {:listing_id listing-uuid :image_id image-uuid})
    (s3/put-object cred bucket (str image-uuid ".jpg") img-file {:content-type "image/jpeg"})
    (redirect "/dashboard")
    ))

(defroutes app-routes
  (route/resources "/") ;Serve static resources at the root path
  (GET "/" req (-> (unauth-page "" req)
                   (content-type "text/html"))) 
  (GET  "/ws" req (ring-ajax-get-or-ws-handshake req))
  (POST "/ws" req (ring-ajax-post                req))
  (route/not-found "Page not found"))

(def backend (session-backend))

(def http-handler
  (-> app-routes
      (wrap-defaults site-defaults)
      (wrap-authentication backend)
      (wrap-authorization backend)
      (wrap-session)
      (wrap-params)
      (wrap-keyword-params)
      (wrap-stacktrace))) 

(defn event-loop
  "Handle inbound events."
  []
  (go (loop [{:keys [client-uuid ring-req event] :as data} (<! ch-chsk)]
        ;(println (str "\n\n\n" ring-req "\n\n\n"))
        (thread (handle-event event ring-req))
        (recur (<! ch-chsk)))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (println (format "Starting web server on port %d." port))
    (run-server (wrap-reload http-handler {:dir "resources/public/templates"}) {:port port})
    (event-loop)))
 
(println 1)
