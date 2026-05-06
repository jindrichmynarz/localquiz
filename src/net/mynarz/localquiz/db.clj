(ns net.mynarz.localquiz.db
  (:require [net.mynarz.localquiz.config :refer [config]]
            [datahike.api :as d]
            [datahike-lmdb.core]
            [mount.core :refer [defstate]]))

(def game-states
  #{:new
    :question
    :voting
    :show-answers
    :leaderboard})

(defn valid-game-state?
  [db eid]
  (->> eid
       (d/entity db)
       :game/state
       game-states))

(def schema
  [{:db/ident :game/id
    :db/doc "Identifier of a game"
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :game/state
    :db/doc "State of a game"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident :game/questions
    :db/doc "Questions in a game stored as EDN strings"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many}
   {:db/ident :game/current-question
    :db/doc "Current question of a game stored as an EDN string"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :game/questions-total
    :db/doc "Number of questions in the game"
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :game/answers
    :db/doc "Answers to the current question"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true}
   {:db/ident :answer/player
    :db/doc "Player who gave the answer"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :answer/answer
    :db/doc "Answer to the current question"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :answer/correct?
    :db/doc "Is the answer correct?"
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}
   {:db/ident :answer/score
    :db/doc "The answer's score"
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one}
   {:db/ident :answer/vote
    :db/doc "Player's vote in a :crowd question"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :game/players
    :db/doc "Players of a game"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true}
   {:db/ident :player/id
    :db/doc "Identifier of a player"
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :player/name
    :db/doc "Player's name"
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :player/score
    :db/doc "Player's score"
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one}
   {:db/ident :game
    :db.entity/attrs [:game/id :game/state :game/questions]
    :db.entity/preds ['net.mynarz.localquiz.db/valid-game-state?]}
   {:db/ident :player
    :db.entity/attrs [:player/id
                      :player/name]}])

(defstate db-config
  :start {:initial-tx schema
          :keep-history? true
          :schema-flexibility :write
          :store (:db-store config)})

(defstate ^{:on-reload :noop} db-conn
  :start (do (d/create-database db-config)
             (d/connect db-config))
  :stop (do (d/release db-conn)
            (d/delete-database db-config)))
