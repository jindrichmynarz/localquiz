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
  (d/q '[:find ?game-id
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
                     [?e _ _ ?tx]
                     [?tx :db/txInstant ?inst]]
            (d/history @db-conn))
       first))

(defn delete-idle-games!
  "Delete games that are idle for a configured time."
  []
  (let [threshold (.minus (Instant/now) ^int (:game-idle-time config) ChronoUnit/HOURS)]
    (log/infof "Deleting the games idle since %s." threshold)
    (->> (current-games)
         (map game-last-modified)
         (filter (comp (partial < threshold) :last-modified))
         (mapv (comp (partial vector :db.purge/entity) :game))
         (d/transact db-conn))))

(defstate ^ScheduledExecutorService game-sweeper
  :start (let [game-idle-time (:game-idle-time config)]
           (doto (Executors/newSingleThreadScheduledExecutor)
                 (.scheduleAtFixedRate delete-idle-games! game-idle-time game-idle-time TimeUnit/HOURS)))
  :stop (do (.shutdown game-sweeper)
            (.awaitTermination game-sweeper 5 TimeUnit/SECONDS)))
