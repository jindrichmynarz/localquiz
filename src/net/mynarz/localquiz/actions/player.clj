(ns net.mynarz.localquiz.actions.player
  (:require [net.mynarz.localquiz.actions.common :refer [refresh-event!]]
            [net.mynarz.localquiz.game :as game]
            [taoensso.timbre :as log]))

(defn validate-player-name!
  "Test if `player-name` is valid."
  [{{{:keys [player-name]} :form} :parameters
    {:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]}]
  (let [validation-error (game/validate-player-name game-id player-name)
        signals (if validation-error
                  {:error (tr [validation-error])}
                  {:error false})]
    (refresh-event! game-id player-id {:signals signals})
    (not validation-error)))

(defn join-game!
  "Add a player with `player-name` to the game identified by `game-id`."
  [{{{:keys [player-name]} :form} :parameters
    {:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (when (validate-player-name! request)
    (log/infof "Player %s is joining game %s as '%s'." player-id game-id player-name)
    (try
      (game/join-game! game-id player-id player-name)
      (catch Exception e
        (when (= (:error (ex-data e)) :game-already-started)
          (refresh-event! game-id player-id {:signals {:error (tr [:errors/game-not-joinable])}}))))))

(defn answer-question!
  [{{answer "answer"} :form-params
    {:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]}]
  (let [{:keys [error]} (game/answer-question! game-id player-id answer)]
    (when error
      (refresh-event! game-id player-id {:signals {:error (tr [error])}}))))

(defn leave-game!
  [{{:keys [game-id]} :path-params
    player-id :sid}]
  (game/disconnect-player! player-id)
  (refresh-event! game-id player-id {:redirect "/"}))
