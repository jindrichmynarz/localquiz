(ns net.mynarz.localquiz.actions.common
  (:require [net.mynarz.localquiz.async :refer [refresh-channel]]
            [clojure.core.async :as a]))

(defn refresh-signals!
  "Refresh `signals` for `session-id` in `game-id`."
  [^String game-id
   ^String session-id
   signals]
  (a/>!! refresh-channel
         {:game-id game-id
          :signals {session-id signals}}))
