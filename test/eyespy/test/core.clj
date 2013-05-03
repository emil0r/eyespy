(ns eyespy.test.core
  (:require [clojure.java.io :as io])
  (:use eyespy.core
        midje.sweet))



(def settings-edn (read-string (slurp "test/settings.edn")))

(fact
 "return-files strings"
 (let [files (return-files (-> settings-edn :project1 :notify))]
   (-> files deref (get "test/watch.css") :alias))
 => "test/watch.css")



(fact
 "return-files map"
 (let [files (return-files (-> settings-edn :project2 :notify))]
   (-> files deref (get "test/watch.css") :alias))
 => "css/watch.css")
