(ns net.mynarz.localquiz.db
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)))

(def initial-db
  {:players {}
   :questions (->> "questions/femquiz.edn"
                   io/resource
                   io/reader
                   PushbackReader.
                   edn/read
                   :questions
                   shuffle
                   (take 20))})
