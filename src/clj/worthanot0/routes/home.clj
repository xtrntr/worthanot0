(ns worthanot0.routes.home
  (:require [worthanot0.dev :refer [is-dev? inject-devmode-html]]
            [worthanot0.security :as sec]
            [worthanot0.db.core :as db]
            [worthanot0.service :as service]         
            
            [buddy.sign.jwe :as jwe]
            [buddy.core.keys :as keys]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]

            [clojure.java.io :as io]
            [clojure.string :refer [join]]

            [compojure.core :refer [GET POST defroutes context]]
            [compojure.route :as route]


            [ring.util.response :refer [resource-response file-response redirect response]]

            [bouncer.core :as b]
            [bouncer.validators :as v]

            [cheshire.core :as json]
            
            [clj-uuid :as uuid]

            [amazonica.core :as amz]
            [aws.sdk.s3 :as s3]

            [net.cgrand.enlive-html :as enlive]
            ))

;; configs
(def bucket "worthanot-listings")
(def aws-zone "ap-southeast-1")
(def access-key "AKIAJ7ZW2CNCTPVYPNIA")
(def secret-key "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")
(def cred {:access-key access-key
           :secret-key secret-key})

(def pubkey (keys/public-key "public.pem"))
(def privkey (keys/private-key "private.pem" "12345"))

(def +namespace+ (uuid/v4))

(defn extract-body [html]
  (enlive/at html [#{:html :body}] enlive/unwrap))

(enlive/deftemplate page (io/resource "templates/nojs.html") 
  [title content]
  [#{:title :h1}] (enlive/content title)
  [:body] (enlive/append content))

(enlive/deftemplate js-page (io/resource "templates/base.html")
  [title content]
  [#{:title :h1}] (enlive/content title)
  [:body] (if is-dev? 
            (inject-devmode-html content) 
            (enlive/prepend content)))

(def index (enlive/html-resource "index.html"))
(def register (enlive/html-resource "register.html"))
(def login (enlive/html-resource "login.html"))
(def dashboard (enlive/html-resource "dashboard.html"))
(def upload (enlive/html-resource "upload.html"))

(def registration-page (page "register" register))
(def login-page (page "login" login))
(def fail-login (page "fail" login))

(defn home-page 
  [resp]
  (page "welcome to worthanot" index))

(defn upload-page 
  [resp]
  (page "upload img here" upload))

(defn display-page
  [{session :session :as req}]
  (let [user (:user (jwe/decrypt (get-in session [:token :auth-token]) privkey {:alg :rsa-oaep :enc :a128cbc-hs256}))]
    (page (str "welcome, " user) dashboard)))

;;return true if no user found, return false if user found
;;when custom validator return false, trigger check
(defn duplicate-user?
  [user]
  (not (not-empty (db/get-user {:username user}))))

(defn duplicate-email?
  [email]
  (not (not-empty (db/get-email {:email email}))))

(defn make-token-pair!
  [user]
  {:token {:auth-token (jwe/encrypt {:user user} pubkey {:alg :rsa-oaep :enc :a128cbc-hs256})}})

(defn handle-registration
  [resp]
  (let [params (:params resp)
        input-email (:email params)
        input-password (:pass params)
        input-username (:user params)
        validation-vals (-> params 
                            (b/validate :user [v/required [v/max-count 30]]
                                        :pass [v/required [v/max-count 30]]
                                        ;;if email field is not empty then check whether it's a valid email
                                        :email [[v/email :pre (comp not-empty :email)]])
                            second
                            (b/validate :email [[duplicate-email? :message "email in use already"]])
                            second
                            (b/validate :user [[duplicate-user? :message "username in use already"]]))
        valid? (not (first validation-vals))
        error-map (::b/errors (second validation-vals))
        error-msgs (str "Error: " (join ". " (-> error-map
                                                 vals
                                                 flatten)))
        token (make-token-pair! input-username)] 
    (if valid?
      (do (db/create-user! {:email input-email
                            :password (sec/encrypt-password input-password)
                            :username input-username})
          (-> (redirect "/dashboard") 
              (assoc :session token)))
      (page error-msgs register))))

(defn handle-login
  [req]
  (let [params (:params req)
        input-user (:username params)
        input-pass (:password params)
        session (:session params)
        ;;kind of circular reasoning here. clear up later. we check for db entry before
        ;;doing any kind of validating when it should not be that way
        db-entry (when-let [entry (not-empty (db/get-user {:username input-user}))]
                   (nth entry 0))
        db-hashed-password (:password db-entry)
        user-exists? (fn [user] db-entry)
        password-match? (fn [pass] (sec/check-password db-hashed-password pass))
        validation-vals (-> params
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
        token (make-token-pair! input-user)]
    (if valid?
      (-> (redirect "/dashboard") 
          (assoc :session token))
      (page error-msgs login))))

(defn handle-logout
  [{session :session}]
  (assoc (redirect "/")
    :session (dissoc session :token)))

(defn is-authenticated [{session :session :as req}]
  (not (empty? session)))

(defn upload-to-amazon
  [req]
  (let [img-file (get-in req [:multipart-params "file" :tempfile])
        listing-name (get-in req [:multipart-params "listing"])
        user (:user (jwe/decrypt (get-in req [:session :token :auth-token]) privkey {:alg :rsa-oaep :enc :a128cbc-hs256}))
        user-uuid (:user_id (nth (db/get-user {:username user}) 0))
        image-amazon-uuid  (uuid/v4)
        listing-uuid (uuid/v4)]
    (db/create-listing! {:listing_id listing-uuid
                         :user_id user-uuid})
    (db/create-image! {:listing_id listing-uuid 
                       :image_amazon_key image-amazon-uuid})
    (s3/put-object cred bucket (str image-amazon-uuid) img-file)
    (redirect "/dashboard")
    ;(page (str (:user_id user-uuid)) upload)
    ))

(defroutes public-routes
  (route/resources "/") ;Serve static resources at the root path
  (GET "/" {session :session :as req} (home-page req))
  (GET "/register" [] registration-page)
  (POST "/register" req (handle-registration req))
  (GET "/login" [] login-page)
  (POST "/login" req (handle-login req))
  (GET "/logout" req (handle-logout req))
  (GET "/dashboard" req (restrict display-page {:handler is-authenticated
                                           :redirect "/login"}))
  (GET "/upload" req (restrict upload-page  {:handler is-authenticated
                                                  :redirect "/login"}))
  (POST "/upload" req (restrict upload-to-amazon  {:handler is-authenticated
                                                   :redirect "/login"}))
  (route/not-found "Page not found"))


(defroutes app-routes
  (-> public-routes))
