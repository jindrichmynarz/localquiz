(ns net.mynarz.localquiz.actions.player-test
  (:require [net.mynarz.localquiz.actions.player :as player]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [are deftest is use-fixtures]]))

(use-fixtures :each fixtures/test-db)

(deftest player-name-in-game?
  (are [player-name] (player/player-name-in-game? fixtures/game-id player-name)
       "Jane"
       "JANE")
  (is (not (player/player-name-in-game? fixtures/game-id "Angela"))))

(deftest join-game!
  (are [player-name key-fn] (key-fn (player/join-game! {:body {:player-name player-name}
                                                        :path-params {:game-id fixtures/game-id}
                                                        :sid (crypto/random-unguessable-uid)}))
       "Jane" :error
       "Angela" :success)
  (let [player-name (crypto/random-unguessable-uid)]
    (player/join-game! {:body {:player-name player-name}
                        :path-params {:game-id fixtures/game-id}
                        :sid (crypto/random-unguessable-uid)})
    (is ((set (game/lobby fixtures/game-id)) player-name))))
