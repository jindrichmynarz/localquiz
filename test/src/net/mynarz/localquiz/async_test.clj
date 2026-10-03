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

(deftest topic
  (let [in (a/chan 8)
        pub (a/pub in async/topic)
        renders (a/sub pub ["sid" :render] (a/chan (a/dropping-buffer 1)))
        events (a/sub pub ["sid" :event] (a/chan 16))]
    (a/onto-chan! in [{:session-id "sid"}
                      {:session-id "sid" :redirect "/"}
                      {:session-id "sid"}
                      {:session-id "sid" :signals {:error ""}}]
                  false)
    (is (= {:session-id "sid" :redirect "/"} (test-within-timeout events 100)))
    (is (= {:session-id "sid" :signals {:error ""}} (test-within-timeout events 100)))
    ;; Render refreshes coalesce, but never at the expense of the events.
    (is (= {:session-id "sid"} (test-within-timeout renders 100)))
    (is (= :timeout (test-within-timeout renders 50)))))
