(ns net.mynarz.localquiz.actions.common
  (:require [net.mynarz.localquiz.async :refer [refresh-channel]]
            [clojure.core.async :as a]))

(defn refresh-session!
  "Refresh a session with `event`."
  [{:keys [sid]}
   event]
  (a/>!! refresh-channel (assoc event :session-id sid)))
