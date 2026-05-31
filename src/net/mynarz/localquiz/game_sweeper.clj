(ns net.mynarz.localquiz.game-sweeper
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [datahike.api :as d]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as log])
  (:import (java.time Instant)
           (java.time.temporal ChronoUnit)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

(defn current-games
  "Get current games."
  []
  (d/q '[:find [?game-id ...]
         :where [_ :game/id ?game-id]]
       @db-conn))

(defn game-last-modified
  "Find when the game with `game-id` was last modified."
  [^String game-id]
  (->> game-id
       (d/q '[:find ?game (max ?inst)
              :keys game last-modified
              :in $ ?game-id
              :where [?game :game/id ?game-id]
                     (or-join [?e ?game]
                       [(identity ?game) ?e]
                       [?game :game/players ?e]
                       [?game :game/answers ?e])
                     [?e _ _ ?tx true]
                     [?tx :db/txInstant ?inst]]
            (d/history @db-conn))
       first))

(defn session-eids-for-game
  "Return entity IDs for sessions associated with game entity `eid`."
  [eid]
  (d/q '[:find [?session ...]
         :in $ ?game-eid
         :where (or-join [?sid ?game-eid]
                  [?game-eid :game/id ?sid]
                  (and [?game-eid :game/players ?player]
                       [?player :player/id ?sid]))
                [?session :session/id ?sid]]
       @db-conn eid))

(defn delete-idle-games!
  "Delete games that are idle for a configured time."
  []
  (let [threshold (.minus (Instant/now) ^int (:game-idle-time config) ChronoUnit/HOURS)]
    (log/infof "Deleting the games idle since %s." threshold)
    (->> (current-games)
         (map game-last-modified)
         (filter (comp (partial < threshold) :last-modified))
         (mapcat (fn [{:keys [game]}]
                   (let [session-purges (->> game
                                             session-eids-for-game
                                             (map (partial vector :db.purge/entity)))]
                     (concat session-purges [[:db.purge/entity game]]))))
         vec
         (d/transact db-conn))))

(defstate game-sweeper
  :start (let [game-idle-time (:game-idle-time config)]
           (doto (Executors/newSingleThreadScheduledExecutor)
                 (.scheduleAtFixedRate delete-idle-games! game-idle-time game-idle-time TimeUnit/HOURS)))
  :stop (doto ^ScheduledExecutorService game-sweeper
              (.shutdown)
              (.awaitTermination 5 TimeUnit/SECONDS)))
