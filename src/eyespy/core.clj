(ns eyespy.core
  (:gen-class)
  (:require [aleph.http :as http]
            [clojure.java.io :as io]
            [clojure.set :as cset]
            [fs.core :as fs]
            [lamina.core :as lamina])
  (:use [clojure.java.shell :only [sh]]))

(def running (atom true))
(def -actions (atom {}))
(def directories (atom []))

(defn- return-files [files]
  (doseq [missing-file (filter #(and (not (fs/file? %))
                                     (not (fs/directory? %))) files)]
    (println missing-file "can't be found"))
  (atom (apply merge {} (map (fn [f] {f {:mod (fs/mod-time f) :changed? false}}) (filter fs/file? files)))))

(defn- read-settings [file profile]
  (let [data ((read-string (slurp file)) (keyword profile))
        {:keys [notify actions]} data]
    (doseq [{:keys [command watch-dir pattern]} actions]
      (swap! directories conj {:directory watch-dir :pattern (re-pattern pattern)})
      (swap! -actions assoc command (return-files (map #(str watch-dir %) (fs/list-dir watch-dir)))))
    (return-files notify)))

(defn- watchable-files [args]
  (case (first args)
    "--watch" (if (fs/file? (second args))
                (let [files (line-seq (io/reader (second args)))]
                  (return-files files))
                (println "Missing argument to --watch"))
    "--settings" (if (and
                      (fs/file? (second args))
                      (not (nil? (nth args 2))))
                   (read-settings (second args) (nth args 2))
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

(defn- check-directories []
  (doseq [[_ files] @-actions]
    (doseq [{:keys [directory pattern]} @directories]
      (let [listed (filter #(re-find pattern %) (fs/list-dir directory))
            files-in-dir (map #(-> % (clojure.string/replace (re-pattern directory) "") (clojure.string/replace #"/" ""))
                              (filter #(.startsWith % directory) (keys @files)))
            files-missing (cset/difference (set files-in-dir) (set listed))
            files-missing-dir (cset/difference (set listed) (set files-in-dir))]
        (doseq [file files-missing-dir]
          (let [file-to-add (str directory file)]
            (if (not (fs/directory? file-to-add))
              (do
                (println "Adding file to watch-list ->" file-to-add)
                (swap! files assoc file-to-add {file {:mod (fs/mod-time file-to-add) :changed? false}})))))
        (doseq [file files-missing]
          (let [file-to-remove (str directory file)]
            (if (not (fs/directory? file-to-remove))
              (do
                (println "Removing file from watch-list ->" file-to-remove)
                (swap! files dissoc file-to-remove)))))))))

(defn- notify-browsers [files channel]
  (doseq [f (keys @files)]
    (if (:changed? (@files f))
      (do
        (println f "changed")
        (lamina/enqueue channel f)))))

(defn- run-actions []
  (doseq [[command watch] @-actions]
    (if (watch-files watch)
      (let [res (apply sh (clojure.string/split command #" "))]
        (if (= (:exit res) 1)
          (do
            (println (:err res))
            (println "Was running command" command)))))))

(defn- listener-handler [broadcast-channel]
  (fn [ch handshake]
    (println "New connection...")
    (lamina/siphon broadcast-channel ch)
    (lamina/enqueue broadcast-channel "Connected...")))

(defn- generate-javascript []
  (if (fs/file? "eyespy.js")
    (println "eyespy.js already exists. Aborting.")
    (do
      (println "Generating eyespy.js")
      (with-open [writer (io/writer "eyespy.js")]
        (.write writer (slurp (io/resource "eyespy.js"))))
      (println "Done..."))))

(defn- generate-settings []
  (if (fs/file? "settings.conf")
    (println "settings.conf already exists. Aborting.")
    (do
      (println "Generating sample settings file")
      (with-open [writer (io/writer "settings.conf")]
        (.write writer (slurp (io/resource "settings.conf"))))
      (println "Done..."))))

(defn- generate-bash-script []
  (if (fs/file? "eyespy")
    (println "eyespy script already exists. Aborting.")
    (do
      (println "Generating bash script. Don't forget to set it to executable")
      (with-open [writer (io/writer "eyespy")]
        (.write writer (slurp (io/resource "eyespy"))))
      (println "Done..."))))

(defn -main [& args]
  (println "EyeSpy, with my eye...
Licensed to" (slurp (io/resource "license")) "\n\n\n------\n")
  (if (empty? args)
    (println "No args given.
-- options accepted ---
--watch <file> (one file with all the files to watch)
--watch <file1> <file2> <file3> <etc>
--settings <settings-file>
--generate javascript -- generates the javascript file
--generate settings -- generates a sample settings file
--generate bash -- generates a bash script for starting EyeSpy
--generate all -- generates all of the above
")
    (case (first args)
      "--generate" (case (second args)
                     "javascript" (generate-javascript)
                     "settings" (generate-settings)
                     "bash" (generate-bash-script)
                     "all" (do
                             (generate-javascript)
                             (generate-settings)
                             (generate-bash-script))
                     (println "Missing second argument"))
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
                (check-directories)
                (Thread/sleep 100)))
            (println "Stopping EyeSpy...")
            (server)
            (println "Exiting EyeSpy...")))))))

;; (reset! running false)
;; (-main "--settings" "settings.clj")
;;(read-settings "settings.clj")
;; (reset! running true)
