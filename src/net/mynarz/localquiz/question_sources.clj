(ns net.mynarz.localquiz.question-sources
  (:require [clojure.java.io :as io]
            [mount.core :refer [defstate]])
  (:import (java.io File)))

(defstate question-sources
  :start (->> "questions"
              io/resource
              io/as-file
              file-seq
              (filter (fn [^File f] (.isFile f)))
              (reduce (fn [acc ^File f] (assoc acc (.getName f) f)) {})))
