(ns net.mynarz.localquiz.spec-test
  (:require [net.mynarz.localquiz.spec :as s]
            [clojure.spec.alpha :as spec]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]))

(deftest validate
  (testing "A report describes a few problems and counts the rest"
    (is (string/includes? (s/validate (spec/coll-of int?) (vec (repeat 1000 "a")))
                          "... and 990 more problems."))))
