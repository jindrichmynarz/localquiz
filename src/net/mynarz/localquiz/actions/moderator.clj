(ns net.mynarz.localquiz.actions.moderator
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :as util]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

(def advance-game
  "Map of transitions between game statuses"
  ; TODO
  {:lobby :question
   :question {:has-questions :evaluation
              :no-questions :leaderboard}
   :evaluation :question})

(defn pick-questions!
  [{game-id :sid
    :as request}])
  ; TODO
  ; - Destructure POST parameter "questions-upload"
  ; - Transact the questions to the database

(defn create-game!
  "Create a game identified by `game-id`."
  [{game-id :sid
    game :game}]
  (when-not game ; TODO: What should happen if the game already exists? Shall we recreate it?
    (let [questions (->> "questions/femquiz.edn"
                          util/read-edn-resource
                          :questions
                          shuffle
                          (take 20)
                          (map (comp pr-str util/replace-react-fragments)))]
      (log/infof "Creating a new game %s." game-id)
      (d/transact db-conn [{:game/id game-id
                            :game/state :new
                            :game/questions questions}]))))

(defn start-game!
  [{game-id :sid
    {:keys [session-role]} :game}]
  (when (= session-role :moderator)
    (log/infof "Starting the game %s." game-id)
    (game/next-question! game-id)))

(defn leaderboard!
  [{game-id :sid
    {:keys [session-role]} :game}]
  (when (= session-role :moderator)
    (log/infof "Going to leaderboard for the game %s." game-id)
    (game/leaderboard! game-id)))

(defn next-question!
  [{game-id :sid
    {:keys [session-role]} :game}]
  (when (= session-role :moderator) ; FIXME: Is this repeated boilerplate required for security?
    (game/next-question! game-id)))

(defn end-game!
  [{game-id :sid
    {:keys [session-role]} :game}]
  (when (= session-role :moderator)
    (log/infof "Ending the game %s." game-id)
    (game/end-game! game-id)))
