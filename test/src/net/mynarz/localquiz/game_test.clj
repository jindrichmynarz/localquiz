(ns net.mynarz.localquiz.game-test
  (:require [net.mynarz.localquiz.db :as db]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [datahike.api :as d]))

(use-fixtures :each fixtures/test-db)

(deftest add-score!
  (let [get-score (fn [player-id]
                    (d/q '[:find ?score .
                           :in $ ?player-id
                           :where [?player :player/id ?player-id]
                                  [?player :player/score ?score]]
                         @db/db-conn
                         player-id))
        get-player-id (fn [player-name]
                        (d/q '[:find ?player-id .
                               :in $ ?player-name
                               :where [?player :player/name ?player-name]
                                      [?player :player/id ?player-id]]
                             @db/db-conn
                             player-name))]
    (testing "Player without score"
      (let [player-id (get-player-id "Bob")]
        (game/add-score! player-id 1)
        (is (= (get-score player-id) 1))))
    (testing "Player with score"
      (let [player-id (get-player-id "Alice")]
        (game/add-score! player-id 4)
        (game/add-score! player-id 1)
        (is (= (get-score player-id) 6))))))

(deftest winner
  (let [winner-id (d/q '[:find ?player-id .
                         :in $ ?game-id ?player-name
                         :where [?game :game/id ?game-id]
                                [?game :game/players ?player]
                                [?player :player/id ?player-id]
                                [?player :player/name ?player-name]]
                       @db/db-conn
                       fixtures/game-id
                       "Jane")]
    (is (= (game/winner fixtures/game-id) winner-id))))

(deftest leaderboard
  (is (= (->> fixtures/game-id
              game/leaderboard
              first
              :player-name)
         "Jane")))
