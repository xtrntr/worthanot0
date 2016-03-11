(ns user
  (:require [worthanot0.server :as server]
            [ring.middleware.reload :refer [wrap-reload]]
            [figwheel-sidecar.repl-api :as figwheel]
            [org.httpkit.server :refer [run-server]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            ))

;; Let Clojure warn you when it needs to reflect on types, or when it does math
;; on unboxed numbers. In both cases you should add type annotations to prevent
;; degraded performance.
(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def http-handler
  (wrap-reload #'server/http-handler)) 

(def browser-repl figwheel/cljs-repl)

(def port (or port (env :port) 10555))

(defn start []
  (println port)
  (run-server http-handler port)
  (server/event-loop))
