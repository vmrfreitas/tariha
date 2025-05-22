(ns tariha.core
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [clojure.java.io :as jio]
            [clojure.pprint :as cpp]
            [clojure.edn :as edn])
  (:import
    [java.io File]) ; For File operations and potentially more advanced EDN reading
  (:gen-class))

(def cli-options
  ;; An option with a required argument
  [["-a" "--add TASK" "Task description"
    :id :task-description]
   ["-p" "--priority PRIORITY" "Task priority"
    :id :priority
    :default 99
    :parse-fn #(Integer/parseInt %)]
   ["-c" "--complete ID" "Task id"]
   ["-h" "--help"]])

(defn get-user-home-dir
  "Gets the user's home directory path."
  []
  (System/getProperty "user.home"))

(defn get-app-data-dir-path
  "Constructs the absolute path to the application's hidden data directory.
   Example: /Users/yourusername/.tariha"
  []
  (let [home-dir (get-user-home-dir)
        app-dir-name ".tariha"] ; Hidden by convention with the leading dot
    (str home-dir File/separator app-dir-name)))

(defn ensure-app-data-dir-exists!
  "Ensures the application's data directory (e.g., ~/.tariha) exists.
   Creates it if it's not present.
   Prints errors and exits if directory creation fails.
   Returns the absolute path to the directory."
  []
  (let [dir-path (get-app-data-dir-path)
        app-dir-file (jio/file dir-path)] ; Create a java.io.File object from the path string
    (println (str "cheguei aqui gurizada" dir-path app-dir-file)) 
    (when-not (.exists app-dir-file)
      (try
        (println (str "INFO: Application data directory not found. Attempting to create: " dir-path))
        (.mkdirs app-dir-file) ; Creates the directory.
                               ; Also creates parent directories if they don't exist (though for ~/.tariha, ~ should always exist).
        (println (str "INFO: Successfully created directory: " dir-path))
        (catch SecurityException se
          ;; This is unlikely for a user's home directory unless permissions are highly unusual.
          (binding [*out* *err*] ; Print errors to standard error
            (println (str "ERROR: Permission denied. Could not create directory: " dir-path))
            (println "Please check permissions for your home directory.")
            (println "Details: " (.getMessage se)))
          (System/exit 1)) ; Exit because the app can't store its data
        (catch Exception e
          (binding [*out* *err*]
            (println (str "ERROR: An unexpected error occurred while creating directory: " dir-path))
            (println "Details: " (.getMessage e)))
          (System/exit 1))))
    dir-path)) ; Return the directory path (String)

;; --- Global Definitions for Data Storage ---

;; `defonce` ensures this directory creation logic runs only once when the namespace is loaded.
(defonce app-data-dir (ensure-app-data-dir-exists!))

;; Define the full path to your tasks data file.
(defonce tasks-data-file (jio/file app-data-dir "tasks.edn")) ; tasks.edn will be inside ~/.tariha/


(defn- read-tasks-from-file
  "Reads tasks from the EDN file. Returns a vector of tasks or an empty vector if the file doesn't exist or is empty/invalid."
  []
  (try
    (let [file-content (slurp tasks-data-file)]
      (if (str/blank? file-content) ; Check if the file content is blank
        [] ; Return an empty vector if blank
        (edn/read-string file-content))) ; Otherwise, parse it
    (catch java.io.FileNotFoundException _
      []) ; If file not found, return empty vector
    (catch Exception e ; Catch other potential parsing errors
      (println (str "Error reading or parsing " tasks-data-file ": " (.getMessage e)))
      []))) ; Return empty vector on other errors

(defn- write-tasks-to-file
  "Writes the given tasks vector to the EDN file, pretty-printed."
  [tasks]
  (try
    (with-open [wr (jio/writer tasks-data-file)]
      (.write wr (with-out-str (cpp/pprint (sort-by :priority tasks)))))
    (catch Exception e
      (println (str "Error writing to " tasks-data-file ": " (.getMessage e))))))

(defn- add-task-to-file
  [task]
  (let [current-tasks (read-tasks-from-file)
        updated-tasks (conj current-tasks task)]
    (write-tasks-to-file updated-tasks)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (do
        (println "Usage: tariha [options] action")
        (println "Options:")
        (println summary)
        (System/exit 0))

      errors
      (do
        (println (str/join \newline errors))
        (System/exit 1))

      :else
      (do
        (println "Parsed options:" options)
        (println "Remaining arguments:" arguments)
        ;; Here you would add your logic to handle the task
        (if (:task-description options)
          (do
            (println (str "Adding task: '" (:task-description options)
                          "' with priority: " (:priority options)))
            (add-task-to-file {:id 1 :description (:task-description options) :priority (:priority options) :done false}))
          (println "Not adding test"))))))

;; To test in development with lein run:
;; lein run --add "My new task" --priority 1
;; lein run --help

