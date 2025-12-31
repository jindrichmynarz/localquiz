(ns net.mynarz.localquiz.actions.player
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.views.player :as views]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

(defn validate-player-name
  [{{:keys [game-id]} :path-params
    {:keys [player-name]} :body
    :as request}]
  (if-let [validation-error (game/validate-player-name game-id player-name)]
    (views/player-name-input request validation-error)
    (views/player-name-input request)))

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
                                            :player/name player-name}]}]))))

(defn answer-question!
  [{{:keys [game-id]} :path-params
    player-id :sid
    {:keys [answer]} :body}]
  (let [{:keys [error]} (game/answer-question! game-id player-id answer)]
    [:section#content
     (when error
       [:h2.error error])]))
