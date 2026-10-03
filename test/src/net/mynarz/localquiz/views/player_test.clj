(ns net.mynarz.localquiz.views.player-test
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [net.mynarz.localquiz.views.player :as player]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as h]))

(defn- render-for
  "Render the shared question section of `question` for a new player in `game-id`,
  counting how many times the question is read."
  [game-id question reads]
  (with-redefs [game/current-question (fn [_] (swap! reads inc) question)]
    (h/html (#'player/shared-question-section
             {:path-params {:game-id game-id}
              :sid (crypto/random-unguessable-uid)
              :tempura/locales [:en]
              :tempura/tr fixtures/tr}
             1000))))

(deftest shared-question-section
  (testing "The question is rendered once for all the players in a game"
    (let [game-id (crypto/random-unguessable-uid)
          question {:type :yesno :correct? true}
          reads (atom 0)
          htmls (repeatedly 3 #(render-for game-id question reads))]
      (is (apply = htmls))
      (is (= @reads 1))))
  (testing "An :autocomplete question is rendered for each player"
    (let [game-id (crypto/random-unguessable-uid)
          reads (atom 0)]
      (with-redefs [game/get-search-fragment (constantly nil)]
        (dotimes [_ 2]
          (render-for game-id {:type :autocomplete :choices [{:text "Praha"}]} reads)))
      (is (= @reads 2)))))
