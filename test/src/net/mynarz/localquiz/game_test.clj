(ns net.mynarz.localquiz.game-test
  (:require [net.mynarz.localquiz.db :as db]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [net.mynarz.localquiz.actions.player :as player]
            [clojure.test :refer [are deftest is testing use-fixtures]]
            [datahike.api :as d]))

(defn get-player-db-id
  [^String player-name]
  (d/q '[:find ?player .
         :in $ ?player-name
         :where [?player :player/name ?player-name]]
        @db/db-conn
        player-name))

(defn get-player-id
  [^String player-name]
  (d/q '[:find ?player-id .
         :in $ ?player-name
         :where [?player :player/name ?player-name]
                [?player :player/id ?player-id]]
       @db/db-conn
       player-name))

; TODO: Split the tests that require DB fixtures and the tests that don't.
(use-fixtures :each fixtures/test-db)

(deftest get-game-state
  (is (= (game/get-game-state fixtures/game-id) :new)))

(deftest player-in-game?
  (is (game/player-in-game? fixtures/game-id (get-player-id "Jane"))))

(deftest player-name-valid-length?
  (let [characters (repeat "x")]
    (are [length predicate] (->> characters
                                 (take length)
                                 (apply concat)
                                 game/player-name-valid-length?
                                 predicate)
         5 true?
         50 false?)))

(deftest player-name-in-game?
  (are [player-name] (game/player-name-in-game? fixtures/game-id player-name)
       "Jane"
       "JANE")
  (is (not (game/player-name-in-game? fixtures/game-id "Angela"))))

(deftest lobby
  (is (= (set (game/lobby fixtures/game-id))
         #{"Jane" "Bob"}))
  (let [player-name "Latecomer"]
    (player/join-game! {:form-params {"player-name" player-name}
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
      (let [player (get-player-db-id "Bob")]
        (->> [{:player player :score 1}]
             game/add-scores
             (d/transact db/db-conn))
        (is (= (get-score player) 1.0))))
    (testing "Player with score"
      (let [player (get-player-db-id "Jane")]
        (->> [{:player player :score 4}
              {:player player :score 1}]
             game/add-scores
             (d/transact db/db-conn))
        (is (= (get-score player) 6.0))))))

(deftest winner
  (let [expected-winner-id (get-player-id "Jane")]
    (is (= (game/winner fixtures/game-id) expected-winner-id))))

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
       "[1,3,0,2]" [1 3 0 2]
       "bork" "bork"))

(deftest player-answered?
  (is (not (game/player-answered? (get-player-id "Jane")))))
