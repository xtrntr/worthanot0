(defproject worthanot0 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj" "src/cljs" "dev"]

  :test-paths ["test/clj"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 
                 ;;JSON encoding
                 [cheshire "5.5.0"]
                 ;;validation for cljs and clj
                 [bouncer "1.0.0"]
                 ;;web server
                 [http-kit "2.1.19"]
                 ;;client side routing
                 [secretary "1.2.3"]
                 ;;routing 
                 [ring/ring-mock "0.3.0"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [compojure "1.4.0"]
                 ;;HTML templating 
                 [enlive "1.1.6"]
                 [sablono "0.6.2"]
                 ;;React dependencies for enlive/sablono
                 [cljsjs/react "0.14.3-0"]
                 [cljsjs/react-dom "0.14.3-1"]
                 ;;reactJS wrapper
                 [org.omcljs/om "0.9.0"]
                 
                 ;;[prismatic/om-tools "0.3.11"]
                 ;;[racehub/om-bootstrap "0.5.3"]

                 ;;environment variables handling
                 [environ "1.0.2"]
                 ;;database
                 [org.postgresql/postgresql "9.4-1206-jdbc4"]
                 ;;security
                 [buddy "0.10.0"]
                 ;;database migrations
                 [migratus "0.8.13"]
                 ;;managing software components with runtime state
                 [com.stuartsierra/component "0.3.1"]
                 ;;dependencies 
                 [org.slf4j/slf4j-log4j12 "1.7.9"]
                 ;;library for generating SQL functions
                 [yesql "0.5.2"]
                 ;;http protocol
                 [clj-http "2.1.0"]
                 ;;classpath (add new libs in REPL)
                 [com.cemerick/pomegranate "0.3.0"]
                 ;;amazon aws
                 [clj-aws-s3 "0.3.10" :exclusions [joda-time]]
                 ;;UUID generation
                 [danlentz/clj-uuid "0.1.6"]
                 ;;AJAX/webSockets
                 [com.taoensso/sente "1.8.1"]
                 ] 
                
  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-environ "1.0.1"]
            [lein-ancient "0.6.8"]
            [migratus-lein "0.2.6"]]

  :migratus {:store         :database
             :migration-dir "migrations"
             :migration-table-name "mygrations"
             :db            {:classname   "org.postgresql.Driver"
                             :subprotocol "postgresql"
                             :subname     "//localhost/postgres"
                             :user        "admin"
                             :password    "12345"}}

  :min-lein-version "2.5.3"

  :uberjar-name "worthanot0.jar"

  ;;lein ring server
  :ring {:handler worthanot0.server/http-handler}

  ;;lein run
  :main worthanot0.server

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (run) and
  ;; (browser-repl) live.
  :repl-options {:init-ns user}

  :cljsbuild {:builds 
              {:app 
               {:source-paths ["src/cljs"]
                
                :figwheel true
                ;; Alternatively, you can configure a function to run every time figwheel reloads.
                ;; :figwheel {:on-jsload "hello.core/on-figwheel-reload"}
                
                :compiler {:main worthanot0.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/app.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}}}

  ;; When running figwheel from nREPL, figwheel will read this configuration
  ;; stanza, but it will read it without passing through leiningen's profile
  ;; merging. So don't put a :figwheel section under the :dev profile, it will
  ;; not be picked up, instead configure figwheel here on the top level.
  
  :figwheel {:http-server-root "public"          ;; serve static assets from resources/public/
             :port 3449 
             ;; :server-ip "127.0.0.1"           ;; default
             :css-dirs ["resources/public/css"]  ;; watch and update CSS
             :reload-clj-files true


             ;; Instead of booting a separate server on its own port, we embed
             ;; the server ring handler inside figwheel's http-kit server, so
             ;; assets and API endpoints can all be accessed on the same host
             ;; and port. If you prefer a separate server process then take this
             ;; out and start the server with `lein run`.
             ;; :ring-handler user/http-handler

             ;; Start an nREPL server into the running figwheel process. We
             ;; don't do this, instead we do the opposite, running figwheel from
             ;; an nREPL process, see
             ;; https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl
             ;; :nrepl-port 7888

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             :server-logfile "log/figwheel.log"}

  :doo {:build "test"}

  :profiles {:dev
             {:dependencies [[figwheel "0.5.0-6"]
                             [figwheel-sidecar "0.5.0-6"]
                             [com.cemerick/piggieback "0.2.1"]
                             [org.clojure/tools.nrepl "0.2.12"]]

              :plugins [[lein-figwheel "0.5.0-6"]
                        [lein-doo "0.1.6"]
                        [lein-pdo "0.1.1"]
                        [lein-ring "0.9.7"]]

              :cljsbuild {:builds
                          {:test
                           {:source-paths ["src/cljs" "test/cljs"]
                            :compiler
                            {:output-to "resources/public/js/compiled/testable.js"
                             :main hello.test-runner
                             :optimizations :none}}}}}

             :uberjar
             {:source-paths ^:replace ["src/clj"]
              :hooks [leiningen.cljsbuild]
              :omit-source true
              :aot :all
              :cljsbuild {:builds
                          {:app
                           {:source-paths ^:replace ["src/cljs"]
                            :compiler
                            {:optimizations :advanced
                             :pretty-print false}}}}}})
