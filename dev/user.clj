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

(defrecord Figwheel []
  component/Lifecycle
  (start [_]
    (figwheel/start-figwheel!)
    (server/event-loop))
  (stop [_]
    ;; you may want to restart other components but not Figwheel
    ;; consider commenting out this next line if that is the case
    (figwheel/stop-figwheel!)))

(def system
  (atom
   (component/system-map
    :app-server (run-server http-handler port))))

(defn start []
  (swap! system component/start))

(defn stop []
  (swap! system component/stop))

(defn reload []
  (stop)
  (start))
