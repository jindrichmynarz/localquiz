(ns net.mynarz.localquiz.config
  (:require [net.mynarz.localquiz.util :refer [read-edn-resource]]
            [mount.core :as mount :refer [defstate]]))

(defstate config
  :start (let [environment (System/getProperty "app.env")
               is-dev? (= environment "dev")
               {:keys [host-name port]
                :as config} (merge (read-edn-resource "default_config.edn")
                                   (mount/args))]
           (assoc config :is-dev? is-dev?
                         :url (if is-dev?
                                (format "http://localhost:%d" port)
                                (str "https://" host-name)))))
