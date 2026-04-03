(ns net.mynarz.localquiz.core
  (:gen-class)
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.db-listener]
            [net.mynarz.localquiz.game-sweeper]
            [net.mynarz.localquiz.logging]
            [net.mynarz.localquiz.server]
            [net.mynarz.localquiz.util :refer [long-str]]
            [clojure.java.browse :refer [browse-url]]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [fast-edn.core :as edn]
            [mount.core :as mount])
  (:import (java.util.concurrent Executors)))

(defn- error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (apply long-str errors)))

(defn- exit
  "Exit with @status and message `msg`.
  `status` 0 is OK, `status` 1 indicates error."
  [^Integer status
   ^String msg]
  {:pre [(#{0 1} status)]}
  (println msg)
  (System/exit status))

(def ^:private die
  (partial exit 1))

(def ^:private info
  (partial exit 0))

(def cli-options
  [["-c" "--config CONFIG" "Path to configuration file in EDN"
    :parse-fn (comp edn/read-once io/as-file)]
   ["-h" "--help" "Display help message"]])

(defn main
  [{:keys [config]
    :or {config {}}}]
  ;; Make futures use virtual threads
  (set-agent-send-executor!
    (Executors/newVirtualThreadPerTaskExecutor))

  (set-agent-send-off-executor!
    (Executors/newVirtualThreadPerTaskExecutor))

  ; Initialize logging to standard error stream
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (mount/stop)
                               (shutdown-agents))))
  (mount/start-with-args config))

(defn -main
  [& args]
  (let [{{:keys [help]} :options
         :keys [errors options summary]} (parse-opts args cli-options)]
    (cond help (info summary)
          errors (die (error-msg errors))
          :else (main options))))

(comment
  ; Start the application
  (main {})

  ; Open the application in the browser
  (browse-url (:url config))

  (mount/stop))
