(ns net.mynarz.localquiz.game-test
  (:require [net.mynarz.localquiz.db :as db]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [net.mynarz.localquiz.actions.player :as player]
            [clojure.test :refer [are deftest is testing use-fixtures]]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

(defn db-empty?
  "Test if the database is empty."
  []
  (->> @db/db-conn
       (d/q '[:find ?e ?a ?v
              :where [?e ?a ?v]
              (not (or [?e :db/ident _] ; Exclude schema entities which all have :db/ident.
                       [?e :db/txInstant _]))]) ; TODO: Where do the remaining timestamps come from?
       empty?))

(defn get-player-id
  [player-name]
  (d/q '[:find ?player .
         :in $ ?player-name
         :where [?player :player/name ?player-name]]
        @db/db-conn
        player-name))

(use-fixtures :each fixtures/test-db)

(deftest player-name-in-game?
  (are [player-name] (game/player-name-in-game? fixtures/game-id player-name)
       "Jane"
       "JANE")
  (is (not (game/player-name-in-game? fixtures/game-id "Angela"))))

(deftest lobby
  (is (= (set (game/lobby fixtures/game-id))
         #{"Jane" "Bob"}))
  (let [player-name "Latecomer"]
    (player/join-game! {:body {:player-name player-name}
                        :path-params {:game-id fixtures/game-id}
                        :sid (crypto/random-unguessable-uid)})
    (is (= (last (game/lobby fixtures/game-id)) player-name))))

(deftest next-question!
  (game/next-question! fixtures/game-id)
  (is (= (game/current-question fixtures/game-id) fixtures/question)))

(deftest add-score!
  (let [get-score (fn [player]
                    (d/q '[:find ?score .
                           :in $ ?player
                           :where [?player :player/score ?score]]
                         @db/db-conn
                         player))]
    (testing "Player without score"
      (let [player (get-player-id "Bob")]
        (game/add-scores! [{:player player :score 1}])
        (is (= (get-score player) 1))))
    (testing "Player with score"
      (let [player (get-player-id "Jane")]
        (game/add-scores! [{:player player :score 4}
                           {:player player :score 1}])
        (is (= (get-score player) 6))))))

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

(deftest parse-answer
  (are [answer parsed-answer] (= (game/parse-answer answer) parsed-answer)
       "true" true
       "false" false
       "0" 0
       "0.123456" 0.123456
       "bork" "bork"))

(deftest end-game!
  (game/end-game! fixtures/game-id)
  (is (db-empty?)))
