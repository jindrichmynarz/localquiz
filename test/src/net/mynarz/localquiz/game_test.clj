(ns net.mynarz.localquiz.game-test
  (:require [net.mynarz.localquiz.db :as db]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [net.mynarz.localquiz.actions.player :as player]
            [clojure.test :refer [are deftest is testing use-fixtures]]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

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
    (player/join-game! {:body {:playerName player-name}
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
        (->> [{:player player :score 1}]
             game/add-scores
             (d/transact db/db-conn))
        (is (= (get-score player) 1.0))))
    (testing "Player with score"
      (let [player (get-player-id "Jane")]
        (->> [{:player player :score 4}
              {:player player :score 1}]
             game/add-scores
             (d/transact db/db-conn))
        (is (= (get-score player) 6.0))))))

(deftest winner
  (is (= (game/winner fixtures/game-id) "Jane")))

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

(deftest score-answers
  (are [question answers scores] (= (map :score (game/score-answers question answers)) scores)
       {:type :multiple
        :choices [{:correct? true} {} {} {}]}
       [{:answer 0} {:answer 3} {:answer 0}]
       [1.0 0.0 1.0]

       {:type :yesno
        :correct? false}
       [{:answer false} {:answer true} {:answer false}]
       [1.0 0.0 1.0]))

(deftest consensus-scoring
  (are [answers scores] (= (map :score (game/consensus-scoring answers)) scores)
       [{:answer 1} {:answer 3} {:answer 1}]
       [1.0 0.0 1.0]

       [{:answer false} {:answer true} {:answer false}]
       [1.0 0.0 1.0]))
