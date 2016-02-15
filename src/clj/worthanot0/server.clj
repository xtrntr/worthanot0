(ns worthanot0.server
  (:require [clojure.java.io :as io]
            [environ.core :refer [env]]
            
            [worthanot0.db.core :as db]
            [worthanot0.routes.home :refer [app-routes]]
            [worthanot0.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :as handler]

            [net.cgrand.enlive-html :refer [deftemplate]]
            [net.cgrand.reload :refer [auto-reload]]

            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.params :refer [wrap-params]]
            ;[ring.middleware.keyword-params :refer [wrap-keyword-params]]

            [buddy.core.keys :as keys]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]])
  (:gen-class))

(def backend (session-backend))

(defn wrap-user [handler]
  (fn [{user-id :identity :as req}]
    (handler (assoc req :user (db/get-user {:username user-id})))))

(def app
  (if is-dev?
    (-> (handler/site #'app-routes {:session {:cookie-name "demo-session"
                                              :cookie-attrs { :max-age 3600}}})
        (wrap-user)
        (wrap-authentication backend)
        (wrap-authorization backend)
        (wrap-session)
        (wrap-params)
        (wrap-stacktrace)
        (wrap-reload))
    (-> app-routes 
        (wrap-defaults api-defaults))))

(defn run-web-server [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (println (format "Starting web server on port %d." port))
    (run-jetty app {:port port :join? false})))

(defn run-auto-reload [& [port]]
  (auto-reload *ns*)
  (start-figwheel))

(defn run [& [port]]
  (when is-dev?
    (run-auto-reload))
  (run-web-server port))

(defn -main [& [port]]
  (run port))
