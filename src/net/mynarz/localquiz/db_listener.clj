(ns net.mynarz.localquiz.db-listener
  (:require [net.mynarz.localquiz.async :refer [refresh-channel]]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [clojure.core.async :as a]
            [datahike.api :as d]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as log]))

(def updated-session-ids-query
  '[:find [?session-id ...]
    :in $ [?entity ...]
    :where (or-join [?entity ?session-id]
              [?entity :game/id ?session-id]
              (and [?game ?attr ?entity]
                   [?component :db/ident ?attr]
                   [?component :db/isComponent true]
                   (or-join [?session-id ?game]
                      [?game :game/id ?session-id]
                      (and [?game :game/players ?player]
                           [?player :player/id ?session-id])))
              [?entity :session/id ?session-id])])

(defn find-updated-sessions
  "Given transaction report, find session IDs of all affected sessions."
  [{:keys [db-after db-before tx-data]}]
  (let [added (->> tx-data
                   (filter last)
                   (map first)
                   distinct)
        retracted (->> tx-data
                       (remove last)
                       (map first)
                       distinct)]
    (or (and added (d/q updated-session-ids-query db-after added))
        (and retracted (d/q updated-session-ids-query db-before retracted)))))

(defn refresh
  "Given the database transaction report `tx-report`, publish refresh events for all affected sessions."
  [tx-report]
  (doseq [sid (find-updated-sessions tx-report)]
    (log/infof "Refreshing session %s." sid)
    (a/>!! refresh-channel {:session-id sid})))

(defstate db-listener
  :start (d/listen db-conn :refresh refresh)
  :stop (d/unlisten db-conn :refresh))
