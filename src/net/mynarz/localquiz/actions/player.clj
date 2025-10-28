(ns net.mynarz.localquiz.actions.player
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.util :refer [now]]
            [clojure.string :as string]
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
         seq)))

(defn join-game!
  "Add `player` to the game identified by `game-id`."
  [{{:keys [game-id]} :path-params
    player-id :sid
    :as request}]
   ;; {player-name :player/name ; TODO: Fix the parameters
   ;;  :as player}]
  (log/info request))
  ;; (if (player-name-in-game? game-id player-name)
  ;;   {:error (format "Player with the name '%s' is already in this game!" player-name)}
  ;;   {:success (d/transact db-conn [{:db/id [:game/id game-id]
  ;;                                   :game/players [(assoc player :player/time-joined (now))]}])}))

(defn answer-question!
  [^String game-id
   ^String player-id])
  ; TODO
  ; Answer submitted via a POST request
  ; Evaluate the answer (i.e. calculate the answer score)
  ; Adjust the player's score
  ; (d/transact db-conn [{:db/id [:player/id player-id]}]))
