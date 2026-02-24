(ns net.mynarz.localquiz.db-listener-test
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.db-listener :as db-listener]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [are deftest is use-fixtures]]
            [datahike.api :as d]))

(use-fixtures :each fixtures/test-db)

(deftest find-updated-game
  (let [player-id (d/q '[:find ?player-id .
                         :in $ ?game-id
                         :where [?game :game/id ?game-id]
                         [?game :game/players ?player]
                         [?player :player/id ?player-id]]
                       @db-conn
                       fixtures/game-id)]
    (are [tx-data] (let [tx-report (d/transact db-conn tx-data)]
                     (is (= (db-listener/find-updated-game tx-report) fixtures/game-id)))
      [{:game/id fixtures/game-id
        :game/players [{:player/id player-id}]}]
      [{:db/id [:player/id player-id]
        :player/score 1.0}])))
