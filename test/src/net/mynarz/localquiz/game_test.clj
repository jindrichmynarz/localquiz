(ns net.mynarz.localquiz.game-test
  (:require [net.mynarz.localquiz.game :as game]
            [clojure.test :refer [are deftest]]))

(deftest player-name-valid-length?
  (let [characters (repeat "x")]
    (are [length predicate] (->> characters
                                 (take length)
                                 (apply str)
                                 game/player-name-valid?
                                 predicate)
         5 true?
         50 false?)))

(deftest parse-answer
  (are [answer parsed-answer] (= (game/parse-answer answer) parsed-answer)
       "true" true
       "false" false
       "0" 0
       "0.123456" 0.123456
       "[1,3,0,2]" [1 3 0 2]
       "bork" "bork"))
