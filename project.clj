(defproject eyespy "1.1.1"
  :description "A CSS Reloader"
  :url "http://emil0r.com/eyespy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [aleph "0.3.0-beta7"]
                 [fs "1.3.2"]]
  :profiles {:dev {:dependencies [[midje "1.4.0"]
                                  [com.stuartsierra/lazytest "1.2.3"]]}}
  :repositories {"stuart" "http://stuartsierra.com/maven2"}
  :main eyespy.core)
