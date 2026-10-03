(ns net.mynarz.localquiz.db-listener
  (:require [net.mynarz.localquiz.async :refer [refresh-channel]]
            [net.mynarz.localquiz.db :refer [db-conn schema]]
            [net.mynarz.localquiz.util :as util]
            [clojure.core.async :as a]
            [datahike.api :as d]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as log])
  (:import (java.util ArrayList)
           (java.util.concurrent BlockingQueue LinkedBlockingQueue)))

(def ^:private owners
  "Reverse attributes leading from a component entity, such as a player or an answer, to
  the game that owns it."
  (->> schema
       (filter :db/isComponent)
       (mapv (fn [{:db/keys [ident]}]
               (keyword (namespace ident) (str "_" (name ident)))))))

(defn- touched-sessions
  "Session IDs affected by the entities `eids` in `db`: a session itself, or every session
  in a game that is or owns one of the entities."
  [db eids]
  (let [entities (map (partial d/entity db) eids)
        games (into #{}
                    (keep (fn [entity]
                            (if (:game/id entity)
                              entity
                              (some #(% entity) owners))))
                    entities)]
    (concat (keep :session/id entities)
            (mapcat (fn [game]
                      (cons (:game/moderator game)
                            (keep :player/id (:game/players game))))
                    games))))

(defn find-updated-sessions
  "Given transaction report, find session IDs of all affected sessions. Walks from each
  entity to its session or game, rather than querying, which would join the entities with
  every game and session, running out of memory on large transactions."
  [{:keys [db-after db-before tx-data]}]
  (let [eids (fn [added?]
               (into #{} (comp (filter #(= added? (boolean (last %)))) (map first)) tx-data))]
    (distinct (concat (touched-sessions db-after (eids true))
                      (touched-sessions db-before (eids false))))))

(defn refresh!
  "Given the database transaction reports `tx-reports`, publish one refresh event for each
  session they affect."
  [tx-reports]
  (doseq [sid (into #{} (mapcat find-updated-sessions) tx-reports)]
    ;; Blocks this thread, never the writer, when the subscribers fall behind.
    (a/>!! refresh-channel {:session-id sid})))

(defstate ^BlockingQueue tx-reports
  "Transaction reports waiting for their sessions to be refreshed.
  ponytail: unbounded, so if the refresher falls far behind, reports pile up in memory."
  :start (LinkedBlockingQueue.))

(defstate db-listener
  "Hands each transaction report off, so that the refresh never holds up Datahike's writer."
  :start (d/listen db-conn :refresh #(.offer tx-reports %))
  :stop (d/unlisten db-conn :refresh))

(defstate ^Thread refresher
  "Refreshes the sessions affected by the waiting transaction reports, a batch at a time,
  so that a burst of transactions refreshes each session once."
  :start (util/thread
           (try
             (while true
               (let [batch (doto (ArrayList.)
                             (.add (.take tx-reports)))]
                 (.drainTo tx-reports batch)
                 (try
                   (refresh! batch)
                   (catch InterruptedException e
                     (throw e))
                   (catch Exception e
                     (log/error e "Refreshing sessions failed.")))))
             (catch InterruptedException _)))
  ;; Waits, so that it does not refresh using the states stopped after it.
  :stop (doto refresher .interrupt (.join 1000)))
