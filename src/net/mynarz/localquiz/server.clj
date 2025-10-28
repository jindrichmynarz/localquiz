(ns net.mynarz.localquiz.server
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.handler :refer [->handler]]
            [net.mynarz.localquiz.middleware :refer [reloading-ring-handler]]
            [mount.core :refer [defstate]]
            [org.httpkit.server :as hk]
            [taoensso.timbre :as log]))

(def dev?
  (= (System/getProperty "app.env" "prod") "dev"))

(defstate ^{:on-reload :noop} server
  :start (let [port (:port config)
               handler* (if dev?
                          (reloading-ring-handler ->handler)
                          (->handler))]
           (log/info "Starting a server on port:" port)
           (hk/run-server handler*
                          {:legacy-return-value? false
                           :port port}))
  :stop (do
          (log/info "Stopping the server")
          (hk/server-stop! server)))
