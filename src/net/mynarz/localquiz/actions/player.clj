(ns net.mynarz.localquiz.actions.player
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.views.player :as views]
            [net.mynarz.localquiz.util :refer [now]]
            [clojure.string :as string]
            [dev.onionpancakes.chassis.compiler :as cc]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

(defn player-name-in-game?
  "Test if a player with `player-name` is already in the game identified by `game-id`.
  Uses case-insensitive matching."
  [^String game-id
   ^String player-name]
  (let [query '[:find ?player
                :in $ ?game-id ?player-name
                :where [?game :game/id ?game-id]
                       [?game :game/players ?player]
                       [?player :player/name ?original-player-name]
                       [(.toLowerCase ^String ?original-player-name) ?lowercase-player-name]
                       [(= ?player-name ?lowercase-player-name)]]]
    (->> player-name
         string/lower-case
         (d/q query @db-conn game-id)
         seq
         some?)))

(defn player-name-valid-length?
  "Test if `player-name` is between 1 and 20 characters."
  [^String player-name]
  (< 1 (count player-name) 20))

(defn validate-player-name
  [{{:keys [game-id]} :path-params
    {:keys [player-name]} :body
    :as request}]
  (when-let [validation-error (cond
                                 (player-name-in-game? game-id player-name)
                                 (format "A player named '%s' is already in this game." player-name)

                                 (player-name-valid-length? player-name)
                                 (format "Player name must have between 1 to 20 characters."))]
    (views/player-name-input request validation-error)))

(defn join-game!
  "Add `player` to the game identified by `game-id`."
  [{{:keys [game-id]} :path-params
    player-id :sid
    {:keys [player-name]} :body}]
  (log/infof "Player %s is joining game %s as '%s'." player-id game-id player-name)
  (d/transact db-conn [{:db/id [:game/id game-id]
                        :game/players [{:player/id player-id
                                        :player/name player-name
                                        :player/time-joined (now)}]}]))

(defn answer-question!
  [^String game-id
   ^String player-id])
  ; TODO
  ; Answer submitted via a POST request
  ; Evaluate the answer (i.e. calculate the answer score)
  ; Adjust the player's score
  ; (d/transact db-conn [{:db/id [:player/id player-id]}]))
