(ns worthanot0.security
  (:require [compojure.core :refer [defroutes GET POST]]
            [ring.util.response :as response]
            [clj-http.client :as http]
            [buddy.sign.jws :as jws]
            [buddy.core.keys :as ks]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [clojure.java.io :as io]
            [buddy.hashers :as hash]))

;; db -> db-spec

(defn encrypt-password
  [password]
  (hash/encrypt password))

(defn check-password
  [encrypted unencrypted]
  (hash/check unencrypted encrypted))
