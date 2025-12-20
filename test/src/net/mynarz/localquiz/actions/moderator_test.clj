(ns net.mynarz.localquiz.actions.moderator-test
  (:require [net.mynarz.localquiz.actions.moderator :as moderator]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [deftest is use-fixtures]]))

(use-fixtures :each fixtures/test-db)

(deftest next-question!
  (is (= (moderator/next-question! fixtures/game-id) fixtures/question))
  (is (nil? (moderator/next-question! fixtures/game-id))))
