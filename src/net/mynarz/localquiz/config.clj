(ns net.mynarz.localquiz.config
  (:require [net.mynarz.localquiz.util :refer [read-edn-resource]]
            [mount.core :refer [defstate]]))

(defstate config
  :start (read-edn-resource ".config.edn"))
