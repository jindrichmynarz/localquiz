(ns net.mynarz.localquiz.async-test
  (:require [net.mynarz.localquiz.async :as async]
            [clojure.core.async :as a]
            [clojure.test :refer [deftest is]]))

(defn test-within-timeout
  "Either get the value from channel `ch` or `:timeout` if it takes more than `ms` milliseconds."
  [ch
   ^long ms]
  (a/alt!! ch ([v] v)
           (a/timeout ms) :timeout))

(deftest throttle
  (let [refresh-rate 50
        in (a/chan)
        out (async/throttle refresh-rate in)]
    (a/go (a/onto-chan! in [:first :second]))
    (is (= :first
           (test-within-timeout out (/ refresh-rate 5))))
    (is (= :timeout
           (test-within-timeout out (/ refresh-rate 5))))
    (is (= :second
           (test-within-timeout out refresh-rate)))))
