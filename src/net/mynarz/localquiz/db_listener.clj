(ns net.mynarz.localquiz.db-listener
  (:require [net.mynarz.localquiz.async :refer [refresh-channel]]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [clojure.core.async :as a]
            [datahike.api :as d]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as log]))

(def updated-game-query
  '[:find ?game-id . ; Only one game can be updated in a transaction.
    :in $ [?entity ...]
    :where (or-join [?entity ?game-id]
                    [?entity :game/id ?game-id] ; Either the entity is a game
                    (and [?game ?attr ?entity]  ; Or it is a component entity of a game
                         [?component :db/ident ?attr]
                         [?component :db/isComponent true]
                         [?game :game/id ?game-id]))])

(defn find-updated-game
  "Given transaction report, find the ID of the game that was updated."
  [{:keys [db-after db-before tx-data]}]
  (let [added (->> tx-data
                   (filter last) ; Added datoms have `true` as their last value.
                   (map first)
                   distinct)
        retracted (->> tx-data
                       (remove last) ; Retracted datoms have `false` as their last value.
                       (map first)
                       distinct)]
    (or (and added (d/q updated-game-query db-after added)) ; Most transactions are additions, so check them first.
        (and retracted (d/q updated-game-query db-before retracted)))))

(defn refresh-game
  "Given the database transaction report `tx-report`, publish the updated game's ID."
  [tx-report]
  (when-let [updated-game (find-updated-game tx-report)]
    (log/infof "The game %s was updated." updated-game)
    (a/>!! refresh-channel updated-game)))

(defstate db-listener
  :start (d/listen db-conn :refresh-game refresh-game)
  :stop (d/unlisten db-conn :refresh-game))
