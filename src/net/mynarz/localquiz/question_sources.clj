(ns net.mynarz.localquiz.question-sources
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [mount.core :refer [defstate]])
  (:import (java.io File)))

(defstate question-sources
  :start (->> "questions"
              io/resource
              io/as-file
              file-seq
              (filter (fn [^File f]
                        (and (.isFile f)
                             (string/ends-with? (.getName f) ".edn"))))
              (reduce (fn [acc ^File f] (assoc acc (.getName f) f)) {})))
