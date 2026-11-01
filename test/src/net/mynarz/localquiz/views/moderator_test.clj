(ns net.mynarz.localquiz.views.moderator-test
  (:require [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [net.mynarz.localquiz.views.moderator :as moderator]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as h]))

(deftest seats
  (testing "Names with the same hash get different ids, which are valid in the DOM."
    (let [ids (map :id (moderator/seats ["Aa" "BB" "Žluťoučký kůň"]))]
      (is (apply distinct? ids))
      (is (every? #(re-matches #"seat-[\w-]+" %) ids))))
  (testing "An odd number of players seats the first at the top."
    (let [[{:keys [cos player sin]}] (moderator/seats ["Ada" "Grace" "Linus"])]
      (is (= "Ada" player))
      (is (= [0.0 -1.0] [cos sin]))))
  (testing "An even number of players leaves the top free for the smoke."
    (is (every? #(> (:sin %) -1.0) (moderator/seats ["Ada" "Grace"]))))
  (testing "Everyone gets a place, in order, evenly around the fire."
    (let [seats (moderator/seats (map str (range 7)))]
      (is (= (map str (range 7)) (map :player seats)))
      (is (= 7 (count (distinct (map (juxt :cos :sin) seats)))))
      (is (< (abs (reduce + (map :cos seats))) 0.01))
      (is (= 7 (count (distinct (map (juxt :grit-x :grit-y) seats)))))))
  (testing "A crowded game sits on an even number of places, with the last one left free."
    (let [places (fn [n] (map (juxt :cos :sin) (moderator/seats (map str (range n)))))]
      (is (= (places 17) (butlast (places 18))))))
  (testing "Below that, everyone sits evenly around one ring."
    (let [seats (moderator/seats (map str (range 15)))]
      (is (= 15 (count seats)))
      (is (< (abs (reduce + (map :cos seats))) 0.01))
      (is (< (abs (reduce + (map :sin seats))) 0.01))))
  (testing "No players, no places."
    (is (empty? (moderator/seats [])))))

(deftest final-leaderboard
  (with-redefs [game/leaderboard (constantly [{:index 1
                                               :player-name "Ada"
                                               :score 1.0
                                               :total-score 1.0
                                               :winner? true}])]
    (let [render (fn [questions-left]
                   (with-redefs [game/game-progress (constantly {:game/questions questions-left
                                                                 :game/questions-total 2})]
                     (h/html (moderator/leaderboard fixtures/tr "game"))))]
      (testing "The last board is titled rather than counted."
        (is (string/includes? (render 0) (fixtures/tr [:final-results])))
        (is (not (string/includes? (render 0) "<progress"))))
      (testing "The others show the progress of the game."
        (is (string/includes? (render 1) "<progress"))
        (is (not (string/includes? (render 1) (fixtures/tr [:final-results])))))
      (testing "Only the last board marks the winner."
        (is (string/includes? (render 0) "class=\"winner\""))
        (is (not (string/includes? (render 1) "class=\"winner\"")))))))
