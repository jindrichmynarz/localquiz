(ns net.mynarz.localquiz.config
  (:require [net.mynarz.localquiz.util :refer [read-edn-resource]]
            [mount.core :refer [defstate]]))

(defstate config
  :start (let [environment (System/getProperty "app.env")
               is-dev? (= environment "dev")
               {:keys [host-name port] :as config} (read-edn-resource ".config.edn")]
           (assoc config
                  :url
                  (if is-dev?
                    (format "http://localhost:%d" port)
                    (str "https://" host-name)))))
