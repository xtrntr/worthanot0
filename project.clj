(defproject worthanot0 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj"]

  :test-paths ["test/clj"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "0.0-3058" :scope "provided"]
                 ;;JSON encoding
                 [cheshire "5.5.0"]
                 ;;validation for cljs and clj
                 [bouncer "1.0.0"]
                 ;;routing 
                 [ring/ring-mock "0.3.0"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.4"]
                 [compojure "1.3.2"]
                 ;;HTML templating 
                 [enlive "1.1.6"]
                 ;;reactJS wrapper
                 [org.omcljs/om "0.8.8"]
                 ;[prismatic/om-tools "0.3.11"]
                 ;[racehub/om-bootstrap "0.5.3"]
                 ;;environment variables handling
                 [environ "1.0.0"]
                 ;;database
                 [org.postgresql/postgresql "9.4-1206-jdbc4"]
                 ;;security
                 [buddy "0.10.0"]
                 ;;database connection management
                 [conman "0.2.9"]
                 ;;database migrations
                 [migratus "0.8.9"]
                 ;;dependencies 
                 [org.slf4j/slf4j-log4j12 "1.7.9"]
                 ;;library for generating SQL functions
                 [yesql "0.5.1"]
                 ;;http protocol
                 [clj-http "2.0.1"]
                 ;;classpath (add new libs in REPL)
                 [com.cemerick/pomegranate "0.3.0"]
                 ;;amazon aws
                 [amazonica "0.3.49"]
                 [clj-aws-s3 "0.3.10" :exclusions [joda-time]]
                 ;;UUID generation
                 [danlentz/clj-uuid "0.1.6"]
                 ] 
                
  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-environ "1.0.0"]
            [lein-ancient "0.6.8"]
            [migratus-lein "0.2.1"]]

  :migratus {:store         :database
             :migration-dir "migrations"
             :migration-table-name "_migrations"
             :db            {:classname   "org.postgresql.Driver"
                             :subprotocol "postgresql"
                             :subname     "//localhost/postgres"
                             :user        "admin"
                             :password    "12345"}}

  :min-lein-version "2.5.0"

  :uberjar-name "worthanot0.jar"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :source-map    "resources/public/js/out.js.map"
                                        :preamble      ["react/react.min.js"]
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :profiles {:dev {:source-paths ["env/dev/clj"]
                   :test-paths ["test/clj"]

                   :dependencies [[figwheel "0.2.5"]
                                  [figwheel-sidecar "0.2.5"]
                                  [com.cemerick/piggieback "0.1.5"]
                                  [weasel "0.6.0"]]

                   :repl-options {:init-ns worthanot0.server
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :plugins [[lein-figwheel "0.2.5"]]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              :css-dirs ["resources/public/css"]
                              :ring-handler worthanot0.server/http-handler}

                   :env {:is-dev true}

                   :cljsbuild {:test-commands { "test" ["phantomjs" "env/test/js/unit-test.js" "env/test/unit-test.html"] }
                               :builds {:app {:source-paths ["env/dev/cljs"]}
                                        :test {:source-paths ["src/cljs" "test/cljs"]
                                               :compiler {:output-to     "resources/public/js/app_test.js"
                                                          :output-dir    "resources/public/js/test"
                                                          :source-map    "resources/public/js/test.js.map"
                                                          :preamble      ["react/react.min.js"]
                                                          :optimizations :whitespace
                                                          :pretty-print  false}}}}}

             :uberjar {:source-paths ["env/prod/clj"]
                       :hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :omit-source true
                       :aot :all
                       :main worthanot0.server
                       :cljsbuild {:builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}})
