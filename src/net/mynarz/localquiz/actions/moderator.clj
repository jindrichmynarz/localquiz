(ns net.mynarz.localquiz.actions.moderator
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.util :refer [read-edn-resource]]
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
                          read-edn-resource
                          :questions
                          shuffle
                          (take 20)
                          (map pr-str))]
      (log/infof "Creating a new game %s." game-id)
      (d/transact db-conn [{:game/id game-id
                            :game/state :new
                            :game/questions questions}]))))

(defn start-game!
  [{game-id :sid
    {:keys [session-role]} :game}]
  (log/infof "Session role: %s" session-role)
  (when (= session-role :moderator)
    (log/infof "Starting the game %s." game-id)
    (d/transact db-conn [{:game-id game-id
                          :game/state :started}])))
