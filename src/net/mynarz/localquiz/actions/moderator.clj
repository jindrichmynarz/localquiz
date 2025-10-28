(ns net.mynarz.localquiz.actions.moderator
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
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

(defn start-game!
  "Start a game identified by `game-id`."
  [^String game-id]
  (let [questions (->> "questions/femquiz.edn"
                       read-edn-resource
                       :questions
                       shuffle
                       (take 20)
                       (map pr-str))]
    (d/transact db-conn [{:game/id game-id
                          :game/questions questions}])
    game-id))

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

(defn end-game!
  "End the game identified by `game-id`."
  [^String game-id]
  (d/transact db-conn [[:db/retractEntity [:game/id game-id]]]))
