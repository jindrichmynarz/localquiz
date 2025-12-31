(ns net.mynarz.localquiz.crypto-test
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [clojure.test :refer [are deftest]]))

(deftest deterministic-shuffle
  (are [coll] (= (crypto/deterministic-shuffle coll) (crypto/deterministic-shuffle coll))
       [0 1 2 4]
       [:a :b :c :d]
       ["foo" "bar" "baz"]))
