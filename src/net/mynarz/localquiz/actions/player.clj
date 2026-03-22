(ns net.mynarz.localquiz.actions.player
  (:require [net.mynarz.localquiz.actions.common :refer [refresh-signals!]]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.game :as game]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

(defn validate-player-name!
  "Test if `player-name` is valid."
  [{{{:keys [player-name]} :form} :parameters
    {:keys [game-id]} :path-params
    player-id :sid}]
  (refresh-signals! game-id player-id {:error (game/validate-player-name game-id player-name)}))

(defn join-game!
  "Add a player with `player-name` to the game identified by `game-id`."
  [{{player-name "player-name"} :form-params
    {:keys [game-id]} :path-params
    player-id :sid}]
  (if-let [validation-error (game/validate-player-name game-id player-name)]
    (refresh-signals! game-id player-id {:error validation-error})
    (do
      (log/infof "Player %s is joining game %s as '%s'." player-id game-id player-name)
      (d/transact db-conn [{:db/id [:game/id game-id]
                            :game/players [{:player/id player-id
                                            :player/name player-name}]}]))))

(defn answer-question!
  [{{{:keys [answer]} :form} :parameters
    {:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]}]
  (let [{:keys [error]} (game/answer-question! game-id player-id answer)]
    (when error
      (refresh-signals! game-id player-id {:error (tr [error])}))))
