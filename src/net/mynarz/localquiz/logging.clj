(ns net.mynarz.localquiz.logging
  (:require [net.mynarz.localquiz.config :refer [config]]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]))

(defstate logging
  :start (let [log-level (if (:is-dev? config) :info :warn)]
           (log/merge-config! {:appenders {:println (appenders/println-appender {:stream :std-err})}
                               :min-level [[#{"*"} log-level]
                                           ; Filter Datahike's verbose logging
                                           [#{"datahike.*" "konserve.*"} :warn]]})))
