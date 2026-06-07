(ns net.mynarz.localquiz.actions.common
  (:require [net.mynarz.localquiz.async :refer [refresh-channel]]
            [net.mynarz.localquiz.game :as game]
            [clojure.core.async :as a]))

(defn refresh-event!
  "Send refresh `event` for `session-id` in `game-id`."
  [^String game-id
   ^String session-id
   event]
  (a/>!! refresh-channel (assoc event :session-id session-id)))

(defn autocomplete-handler
  "Stores the search fragment for `def-id` nested under :autocomplete in session params."
  [{{:keys [def-id]} :path-params
    :keys [signals sid]}]
  (game/merge-session-params! sid
    {:autocomplete {(keyword def-id) (get signals (keyword def-id))}}))
