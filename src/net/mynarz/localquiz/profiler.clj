(ns net.mynarz.localquiz.profiler
  (:require [net.mynarz.localquiz.core :as core]
            [clj-async-profiler.core :as prof]))
            ;; [criterium.core :as crit]))

(comment
  ;; Profile the following expression:
  (prof/profile (core/-main))

  ;; The resulting flamegraph will be stored in /tmp/clj-async-profiler/results/
  ;; You can view the HTML file directly from there or start a local web UI:

  (prof/serve-ui 9090)) ; Serve on port 9090)

;; (comment
;;   (crit/bench (Thread/sleep 1000)))
