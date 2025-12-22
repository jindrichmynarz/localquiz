(ns net.mynarz.localquiz.actions.player
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.views.player :as views]
            [net.mynarz.localquiz.util :refer [now]]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

(defn validate-player-name
  [{{:keys [game-id]} :path-params
    {:keys [player-name]} :body
    :as request}]
  (when-let [validation-error (game/validate-player-name game-id player-name)]
    (views/player-name-input request validation-error)))

(defn join-game!
  "Add `player` to the game identified by `game-id`."
  [{{:keys [game-id]} :path-params
    player-id :sid
    {:keys [player-name]} :body
    :as request}]
  (if-let [validation-error (game/validate-player-name game-id player-name)]
    (views/player-name-input request validation-error)
    (do
      (log/infof "Player %s is joining game %s as '%s'." player-id game-id player-name)
      (d/transact db-conn [{:db/id [:game/id game-id]
                            :game/players [{:player/id player-id
                                            :player/name player-name
                                            :player/time-joined (now)}]}]))))

(defn answer-question!
  [^String game-id
   ^String player-id])
  ; TODO
  ; Answer submitted via a POST request
  ; Evaluate the answer (i.e. calculate the answer score)
  ; Adjust the player's score
  ; (d/transact db-conn [{:db/id [:player/id player-id]}]))
