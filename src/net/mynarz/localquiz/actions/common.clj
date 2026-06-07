(ns net.mynarz.localquiz.actions.common
  (:require [net.mynarz.localquiz.async :refer [refresh-channel]]
            [net.mynarz.localquiz.game :as game]
            [clojure.core.async :as a]))

(defn refresh-session!
  "Refresh a session with `event`."
  [{:keys [sid]}
   event]
  (a/>!! refresh-channel (assoc event :session-id sid)))

(defn autocomplete-handler
  "Stores the search fragment for `def-id` nested under :autocomplete in session params."
  [{{:keys [def-id]} :path-params
    :keys [signals sid]}]
  (game/merge-session-params! sid
    {:autocomplete {(keyword def-id) (get signals (keyword def-id))}}))
