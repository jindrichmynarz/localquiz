(ns net.mynarz.localquiz.actions.player
  (:require [net.mynarz.localquiz.actions.common :refer [refresh-session!]]
            [net.mynarz.localquiz.game :as game]
            [taoensso.timbre :as log]))

(defn validate-player-name!
  "Test if `player-name` is valid."
  [{{{:keys [player-name]} :form} :parameters
    {:keys [game-id]} :path-params
    :tempura/keys [tr]
    :as request}]
  (let [validation-error (game/validate-player-name game-id player-name)
        signals (if validation-error
                  {:error (tr [validation-error])}
                  {:error false})]
    (refresh-session! request {:signals signals})
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
          (refresh-session! request {:signals {:error (tr [:errors/game-not-joinable])}}))))))

(defn autocomplete-handler
  "Stores the player's typed autocomplete fragment (the `answer` form field, sent
  by the `post` helper as form data) under :autocomplete in their session params."
  [{{answer "answer"} :form-params
    :keys [sid]}]
  (game/merge-session-params! sid {:autocomplete answer}))

(defn answer-question!
  [{{answer "answer"} :form-params
    {:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (let [{:keys [error]} (game/answer-question! game-id player-id answer)]
    (when error
      (refresh-session! request {:signals {:error (tr [error])}}))))

(defn leave-game!
  [{player-id :sid
    :as request}]
  (game/disconnect-player! player-id)
  (refresh-session! request {:redirect "/"}))
