(ns net.mynarz.localquiz.views.player
  (:require [net.mynarz.localquiz.views.common :refer [view]]))

(defn join-game
  [{{:keys [game-id]} :path-params}]
  (view [:form
         {:action (str "/" game-id)
          :method "post"
          :name "join-game"}
         [:label
          {:for "player-name"}
          "Player name"]
         [:input
          {:id "player-name"
           :type "text"}]
         [:button
          {:type "submit"}
          "Join game"]]))

(defn player-view
  [{{:keys [game-id]} :path-params
    :as request}])
