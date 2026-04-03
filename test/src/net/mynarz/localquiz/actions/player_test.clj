(ns net.mynarz.localquiz.actions.player-test
  (:require [net.mynarz.localquiz.actions.player :as player]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [are deftest is use-fixtures]]))

(use-fixtures :each fixtures/test-db)

(defn player-params
  [^String player-name]
  {:parameters {:form {:player-name player-name}}
   :path-params {:game-id fixtures/game-id}
   :sid (crypto/random-unguessable-uid)
   :tempura/tr fixtures/tr})

(deftest join-game!
  (are [player-name key-fn] (key-fn (player/join-game! (player-params player-name)))
       "Angela" :tx-data) ; Returns transaction data
  (let [player-name "Felix"]
    (player/join-game! (player-params player-name))
    (is ((set (game/lobby fixtures/game-id)) player-name))))

(deftest join-game!-requires-new-state
  (game/leaderboard! fixtures/game-id)
  (is (thrown? Exception (player/join-game! (player-params "Angela")))))
