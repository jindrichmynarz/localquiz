(ns net.mynarz.localquiz.actions.player
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.views.player :as views]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

(defn validate-player-name
  "Test if `player-name` is valid."
  [{{player-name "player-name"} :form-params
    {:keys [game-id]} :path-params
    :as request}]
  (let [validation-error (game/validate-player-name game-id player-name)]
    (views/player-name-input
      (cond-> request
        validation-error (assoc :error validation-error)))))

(defn join-game!
  "Add a player with `player-name` to the game identified by `game-id`."
  [{{player-name "player-name"} :form-params
    {:keys [game-id]} :path-params
    player-id :sid
    :as request}]
  ; TODO: Avoid duplicating `validate-player-name`.
  (if-let [validation-error (game/validate-player-name game-id player-name)]
    (-> request
        (assoc :error validation-error)
        views/player-name-input)
    (do
      (log/infof "Player %s is joining game %s as '%s'." player-id game-id player-name)
      (d/transact db-conn [{:db/id [:game/id game-id]
                            :game/players [{:player/id player-id
                                            :player/name player-name}]}]))))

(defn answer-question!
  [{{answer "answer"} :form-params
    {:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]}]
  (let [{:keys [error]} (game/answer-question! game-id player-id answer)]
    (when error
      [:section#content
       [:h2.error (tr [error])]])))
