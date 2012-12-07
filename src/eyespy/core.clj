(ns eyespy.core
  (:gen-class)
  (:require [aleph.http :as http]
            [clojure.java.io :as io]
            [fs.core :as fs]
            [lamina.core :as lamina]))

(def running (atom true))

(defn- return-files [files]
  (doseq [missing-file (filter #(not (fs/file? %)) files)]
    (println missing-file "can't be found"))
  (atom (apply merge {} (map (fn [f] {f {:mod (fs/mod-time f) :changed? false}}) (filter fs/file? files)))))

(defn- watchable-files [args]
  (if (= (first args) "--watch")
    (if (fs/file? (second args))
      (let [files (line-seq (io/reader (second args)))]
        (return-files files))
      (println "Missing argument to --watch"))
    (return-files args)))

(defn- watch-files [files]
  (loop [[f & r] (keys @files)
         files files
         changed? false]
    (if (nil? f)
      changed?
      (if (= (:mod (@files f)) (fs/mod-time f))
        (recur r files changed?)
        (recur r (swap! files assoc f {:mod (fs/mod-time f) :changed? true}) true)))))

(defn- notify-browsers [files channel]
  (doseq [f (keys @files)]
    (if (:changed? (@files f))
      (do
        (println f "changed")
        (lamina/enqueue channel f)))))

(defn- listener-handler [broadcast-channel]
  (fn [ch handshake]
    (println "New connection...")
    (lamina/siphon broadcast-channel ch)
    (lamina/enqueue broadcast-channel "Connected...")))

(defn -main [& args]
  (if (empty? args)
    (println "No args
Try --watch <file> (line-seperated files to watch)
or <file1> <file2> <file3> <etc>")
    (if @running
     (let [_ (println "EyeSpy starting...")
           files (watchable-files args)
           broadcast-channel (lamina/permanent-channel)
           server (http/start-http-server (listener-handler broadcast-channel) {:port 4321 :websocket true})]
       (println "EyeSpy started...")
       (while @running
         (let [changed? (watch-files files)]
           (if changed?
             (notify-browsers files broadcast-channel))
           (Thread/sleep 100)))
       (println "Stopping EyeSpy...")
       (server)
       (println "Exiting EyeSpy...")))))

;; (reset! running false)
;; (-main "../angularjs/css/main.css")
;; (reset! running true)