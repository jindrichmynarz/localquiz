(ns net.mynarz.localquiz.actions.moderator
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :refer [read-edn-resource]]
            [clojure.edn :as edn]
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

(defn next-question!
  "Get the next question for `game-id`.
  Removes the question from the game and returns the question in Hiccup
  or nil if there are no more questions."
  [^String game-id]
  (when-let [question (d/q '[:find ?question .
                             :in $ ?game-id
                             :where [?game :game/id ?game-id]
                                    [?game :game/questions ?question]]
                           @db-conn
                           game-id)]
    (d/transact db-conn [[:db/retract [:game/id game-id] :game/questions question]])
    (edn/read-string question)))
