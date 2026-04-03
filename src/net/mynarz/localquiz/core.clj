(ns net.mynarz.localquiz.core
  (:gen-class)
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.db-listener]
            [net.mynarz.localquiz.game-sweeper]
            [net.mynarz.localquiz.logging]
            [net.mynarz.localquiz.server]
            [clojure.java.browse :refer [browse-url]]
            [mount.core :as mount])
  (:import (java.util.concurrent Executors)))

;; Make futures use virtual threads
(set-agent-send-executor!
 (Executors/newVirtualThreadPerTaskExecutor))

(set-agent-send-off-executor!
 (Executors/newVirtualThreadPerTaskExecutor))

(defn -main
  [& _]
  ; Initialize logging to standard error stream
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (mount/stop)
                               (shutdown-agents))))
  (mount/start))

(comment
  ; Start the application
  (-main)

  ; Open the application in the browser
  (browse-url (:url config))

  (mount/stop))
