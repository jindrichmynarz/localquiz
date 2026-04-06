(ns net.mynarz.localquiz.actions.common
  (:require [net.mynarz.localquiz.async :refer [refresh-channel]]
            [clojure.core.async :as a]))

(defn refresh-event!
  "Send refresh `event` for `session-id` in `game-id`."
  [^String game-id
   ^String session-id
   event]
  (a/>!! refresh-channel
         {:game-id game-id
          session-id event}))
