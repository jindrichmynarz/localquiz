(ns net.mynarz.localquiz.views.moderator-test
  (:require [net.mynarz.localquiz.actions.player :as player]
            [net.mynarz.localquiz.views.moderator :as moderator]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [deftest is use-fixtures]]))

(use-fixtures :each fixtures/test-db)

(deftest lobby
  (is (= (set (moderator/lobby fixtures/game-id))
         #{"Jane" "Bob"}))
  (let [player-name "Latecomer"]
    (player/join-game! fixtures/game-id {:player/name player-name})
    (is (= (last (moderator/lobby fixtures/game-id)) player-name))))
