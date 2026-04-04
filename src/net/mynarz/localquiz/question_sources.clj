(ns net.mynarz.localquiz.question-sources
  (:require [clojure.java.io :as io]
            [fast-edn.core :as edn]
            [mount.core :refer [defstate]]))

(defstate question-sources
  :start (with-open [input-stream (-> "questions/index.edn" io/resource io/input-stream)]
           (let [sources (edn/read-once input-stream)]
             (fn [language]
               (sources (or language :en))))))
