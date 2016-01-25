(ns worthanot0.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [worthanot0.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'worthanot0.core-test))
    0
    1))
