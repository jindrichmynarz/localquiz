(ns net.mynarz.localquiz.integration.game-test
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

(defn db-empty?
  "Test if the database is empty."
  []
  (->> @db/db-conn
       (d/q '[:find ?e ?a ?v
              :where [?e ?a ?v]
              (not (or [?e :db/ident _] ; Exclude schema entities which all have :db/ident.
                       [?e :db/txInstant _]))])
       empty?))

(defn game-deleted?
  [^String game-id]
  (->> game-id
       (d/q '[:find (pull ?game [*])
              :in $ ?game-id
              :where [?game :game/id ?game-id]]
            @db/db-conn)
       seq
       not))

(use-fixtures :once fixtures/test-db)

(deftest get-game-state
  (is (= (game/get-game-state fixtures/game-id) :new)))

(deftest player-in-game?
  (is (game/player-in-game? fixtures/game-id (get-player-id "Jane"))))

(deftest player-name-in-game?
  (are [player-name] (game/player-name-in-game? fixtures/game-id player-name)
       "Jane"
       "JANE")
  (is (not (game/player-name-in-game? fixtures/game-id "Angela"))))

(deftest lobby
  (is (= (set (game/lobby fixtures/game-id))
         #{"Jane" "Bob"}))
  (let [player-name "Latecomer"]
    (player/join-game! {:parameters {:form {:player-name player-name}}
                        :path-params {:game-id fixtures/game-id}
                        :sid (crypto/random-unguessable-uid)})
    (is (= (last (game/lobby fixtures/game-id)) player-name))))

(deftest next-question!
  (with-redefs [game/schedule-timeout (fn [_])]
    (game/next-question! fixtures/game-id))
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

(deftest player-answered?
  (is (not (game/player-answered? (get-player-id "Jane")))))

(deftest leaderboard!
  (let [game-id (crypto/random-unguessable-uid)]
    (game/create-game! game-id [(pr-str fixtures/question)])
    (game/leaderboard! game-id)
    (is (= (game/get-game-state game-id) :leaderboard))))

(deftest create-game!
  (let [game-id (crypto/random-unguessable-uid)]
    (game/create-game! game-id [(pr-str fixtures/question)])
    (is (= (game/get-game-state game-id) :new))))

(deftest end-game!
  (let [game-id (crypto/random-unguessable-uid)]
    (game/create-game! game-id [(pr-str fixtures/question)])
    (game/end-game! game-id)
    (game-deleted? game-id)
    (db-empty?)))
