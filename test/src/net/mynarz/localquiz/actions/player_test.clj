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
  (let [player-id (crypto/random-unguessable-uid)]
    (player/join-game! (assoc (player-params "Angela") :sid player-id))
    (is (not (game/player-in-game? fixtures/game-id player-id)))))

(defn- join-error
  "The error `join-game!` shows the player for `request`, or nil if they joined."
  [request]
  (let [errors (atom [])]
    (with-redefs [net.mynarz.localquiz.actions.common/refresh-session!
                  (fn [_ {{:keys [error]} :signals}] (when error (swap! errors conj error)))]
      (player/join-game! request))
    (first @errors)))

(deftest join-game!-requires-not-moderator
  (is (= (fixtures/tr [:errors/moderator-cannot-play])
         (join-error (assoc (player-params "Mod") :sid fixtures/moderator-id))))
  (is (not (game/player-in-game? fixtures/game-id fixtures/moderator-id))))

(deftest join-game!-one-game-per-session
  (let [other-game (crypto/random-unguessable-uid)
        {:keys [sid]
         :as request} (player-params "Sam")]
    (game/create-game! other-game (crypto/random-unguessable-uid) [(pr-str fixtures/question)])
    (is (nil? (join-error request)))
    (is (= (fixtures/tr [:errors/game-already-joined])
           (join-error (assoc-in request [:path-params :game-id] other-game))))
    (is (game/player-in-game? fixtures/game-id sid))
    (is (not (game/player-in-game? other-game sid)))))
