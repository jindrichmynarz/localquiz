(ns net.mynarz.localquiz.game-test
  (:require [net.mynarz.localquiz.db :as db]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [net.mynarz.localquiz.actions.player :as player]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [datahike.api :as d]
            [net.mynarz.localquiz.crypto :as crypto]))

(defn db-empty?
  "Test if the database is empty."
  []
  (->> @db/db-conn
       (d/q '[:find ?e ?a ?v
              :where [?e ?a ?v]
              (not (or [?e :db/ident _]))]) ; Exclude schema entities which all have :db/ident.
       empty?))

(use-fixtures :each fixtures/test-db)

(deftest lobby
  (is (= (set (game/lobby fixtures/game-id))
         #{"Jane" "Bob"}))
  (let [player-name "Latecomer"]
    (player/join-game! {:body {:player-name player-name}
                        :path-params {:game-id fixtures/game-id}
                        :sid (crypto/random-unguessable-uid)})
    (is (= (last (game/lobby fixtures/game-id)) player-name))))

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

(deftest end-game!
  (game/end-game! fixtures/game-id)
  (is (db-empty?)))
