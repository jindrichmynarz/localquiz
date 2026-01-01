(ns net.mynarz.localquiz.actions.player-test
  (:require [net.mynarz.localquiz.actions.player :as player]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [are deftest is use-fixtures]]))

(use-fixtures :each fixtures/test-db)

(deftest join-game!
  (are [player-name key-fn] (key-fn (player/join-game! {:body {:playerName player-name}
                                                        :path-params {:game-id fixtures/game-id}
                                                        :sid (crypto/random-unguessable-uid)
                                                        :tr fixtures/tr}))
       "Jane" vector? ; Returns error Hiccup
       "Angela" :tx-data) ; Returns transaction data
  (let [player-name "Felix"]
    (player/join-game! {:body {:playerName player-name}
                        :path-params {:game-id fixtures/game-id}
                        :sid (crypto/random-unguessable-uid)
                        :tr fixtures/tr})
    (is ((set (game/lobby fixtures/game-id)) player-name))))
