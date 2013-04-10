(ns eyespy.core
  (:gen-class)
  (:require [aleph.http :as http]
            [clojure.java.io :as io]
            [fs.core :as fs]
            [lamina.core :as lamina])
  (:use [clojure.java.shell :only [sh]]))

(def running (atom true))
(def -actions (atom {}))

(defn- return-files [files]
  (doseq [missing-file (filter #(and (not (fs/file? %))
                                     (not (fs/directory? %))) files)]
    (println missing-file "can't be found"))
  (atom (apply merge {} (map (fn [f] {f {:mod (fs/mod-time f) :changed? false}}) (filter fs/file? files)))))

(defn- read-settings [file]
  (let [{:keys [notify actions] :as data} (read-string (slurp file))]
    (doseq [{:keys [command watch-dir]} actions]
      (swap! -actions assoc command (return-files (map #(str watch-dir %) (fs/list-dir watch-dir)))))
    (return-files notify)))

(defn- watchable-files [args]
  (case (first args)
        "--watch" (if (fs/file? (second args))
                    (let [files (line-seq (io/reader (second args)))]
                      (return-files files))
                    (println "Missing argument to --watch"))
        "--settings" (if (fs/file? (second args))
                       (read-settings (second args))
                       (println "Missing argument to --settings"))
        (return-files args)))

(defn- watch-files [files]
  (loop [[f & r] (keys @files)
         changed? false]
    (if (nil? f)
      changed?
      (if (= (:mod (@files f)) (fs/mod-time f))
        (recur r changed?)
        (do
          (swap! files assoc f {:mod (fs/mod-time f) :changed? true})
          (recur r true))))))

(defn- notify-browsers [files channel]
  (doseq [f (keys @files)]
    (if (:changed? (@files f))
      (do
        (println f "changed")
        (lamina/enqueue channel f)))))

(defn- run-actions []
  (doseq [[command watch] @-actions]
    (if (watch-files watch)
      (println "Running command:" command)
      (apply sh (clojure.string/split command #" ")))))

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
      (do
        (println "EyeSpy starting...")
        (let [files (watchable-files args)
              broadcast-channel (lamina/permanent-channel)
              server (http/start-http-server (listener-handler broadcast-channel) {:port 4321 :websocket true})]
          (println "EyeSpy started...")
          (while @running
            (run-actions)
            (let [changed? (watch-files files)]
              (if changed?
                (notify-browsers files broadcast-channel))
              (Thread/sleep 100)))
          (println "Stopping EyeSpy...")
          (server)
          (println "Exiting EyeSpy..."))))))

;;(reset! running false)
;;(-main "--settings" "settings.clj")
;;(read-settings "settings.clj")
;;(reset! running true)

