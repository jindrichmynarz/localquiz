(ns net.mynarz.localquiz.game
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [datahike.api :as d]))

(defn add-score
  "Add `score` to the current score of the player identified by `player-id`."
  [db
   ^String player-id
   ^long score]
  (if-let [{player :db/id
            current-score :player/score
            :or {current-score 0}} (d/q '[:find (pull ?player [:db/id :player/score]) .
                                          :in $ ?player-id
                                          :where [?player :player/id ?player-id]]
                                        db
                                        player-id)]
    [{:db/id player
      :player/score (+ current-score score)}]
    (throw (ex-info (format "No player with ID '%s'!" player-id) {}))))

(defn add-score!
  [^String player-id
   ^long score]
  (d/transact db-conn [[:db.fn/call add-score player-id score]]))

(defn leaderboard
  "Get the player leaderboard for `game-id` using the database `conn`."
  [^String game-id]
  (->> game-id
       (d/q '[:find ?player-id ?player-name ?score
              :in $ ?game-id
              :keys player-id player-name score
              :where [?game :game/id ?game-id]
              [?game :game/players ?player]
              [?player :player/id ?player-id]
              [?player :player/name ?player-name]
              (or-join [?player ?score]
                       [?player :player/score ?score]
                       [(ground 0) ?score])]
            @db-conn)
       (sort-by :score #(compare %2 %1))))

(defn winner
  "Get the ID of the winning player."
  [^String game-id]
  (-> game-id
      leaderboard
      first
      :player-id))
