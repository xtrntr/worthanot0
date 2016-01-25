(ns worthanot0.routes.home
  (:require [clojure.java.io :as io]
            [worthanot0.dev :refer [is-dev? inject-devmode-html]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [net.cgrand.enlive-html :as enlive]
            [ring.util.response :refer [resource-response file-response redirect]]
            [worthanot0.security :as security]
            [worthanot0.db.core :as db]
            ))

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
(def log-in (enlive/html-resource "login.html"))
(def registration-page
  (page "register" register))

(defn handle-registration
  [params]
  (db/create-user! {:email (get-in params [:email])
                    :password (get-in params [:email])
                    :username (get-in params [:user])})
  (registration-page))


(defroutes app-routes
  (route/resources "/") ;Serve static resources at the root path
  (route/resources "/react" {:root "react"})
  (GET "/" [] (page "home page" index))
  (GET "/register" [] registration-page)
  (POST "/register" {params :params} (handle-registration params))
  (route/not-found "Page not found"))

