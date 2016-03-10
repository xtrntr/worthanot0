(ns worthanot0.db.core
  (:require [yesql.core :refer [defqueries]]))

(def db-spec {:classname   "org.postgresql.Driver"
              :subprotocol "postgresql"
              :subname     "//localhost/postgres"
              :user        "admin"
              :password    "12345"})

(defqueries "worthanot0/db/queries.sql"
  {:connection db-spec})   
 
 
