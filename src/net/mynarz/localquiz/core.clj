(ns net.mynarz.localquiz.core
  (:gen-class)
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.db-listener]
            [net.mynarz.localquiz.server :refer [server]] ; Must be imported for Mount to start the server.
            [clojure.java.browse :refer [browse-url]]
            [mount.core :as mount]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders])
  (:import (java.util.concurrent Executors)))

;; Make futures use virtual threads
(set-agent-send-executor!
 (Executors/newVirtualThreadPerTaskExecutor))

(set-agent-send-off-executor!
 (Executors/newVirtualThreadPerTaskExecutor))

(defn -main
  [& _]
  ; Initialize logging to standard error stream
  (log/merge-config! {:appenders {:println (appenders/println-appender {:stream :std-err})}
                      ; Filter Datahike's verbose logging
                      :min-level [[#{"datahike.*" "konserve.*"} :warn]]})
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
