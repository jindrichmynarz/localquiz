(ns net.mynarz.localquiz.db
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.question-spec :as qs]
            [datahike.api :as d]
            [datahike-lmdb.core]
            [mount.core :refer [defstate]]))

(def game-states
  #{:new
    :question
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
   {:db/ident :game/moderator
    :db/doc "Session ID of the game's moderator."
    :db/valueType :db.type/string
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :game/state
    :db/doc "State of a game"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident :game/questions
    :db/doc "Questions in a game stored as EDN strings"
    :db/valueType :db.type/string
    :db/maxLength qs/max-string-length
    :db/cardinality :db.cardinality/many}
   {:db/ident :game/current-question
    :db/doc "Current question of a game stored as an EDN string"
    :db/valueType :db.type/string
    :db/maxLength qs/max-string-length
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
   {:db/ident :def/id
    :db/doc "$def identifier"
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident :def/value
    :db/doc "$def value as an EDN string"
    :db/valueType :db.type/string
    :db/maxLength qs/max-string-length
    :db/cardinality :db.cardinality/one}
   {:db/ident :game/defs
    :db/doc "defs of a game"
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true}
   {:db/ident :game
    :db.entity/attrs [:game/id :game/state :game/questions]
    :db.entity/preds ['net.mynarz.localquiz.db/valid-game-state?]}
   {:db/ident :player
    :db.entity/attrs [:player/id
                      :player/name]}
   {:db/ident :session/id
    :db/doc "Identifier of a session"
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/index true
    :db/cardinality :db.cardinality/one}
   {:db/ident :session/search-fragment
    :db/doc "Text the player has typed into an autocomplete input. Used to filter the
             question's choices server-side. Retracted when the game moves on to the
             next question."
    :db/valueType :db.type/string
    :db/maxLength qs/max-answer-length
    :db/cardinality :db.cardinality/one}])

(defstate db-config
  :start {:initial-tx schema
          :keep-history? true
          :schema-flexibility :write
          :store (:db-store config)
          :value-caps :default})

(defstate ^{:on-reload :noop} db-conn
  :start (do (when-not (d/database-exists? db-config)
               (d/create-database db-config))
             (d/connect db-config))
  :stop (do (d/release db-conn)
            (when (:is-dev? config)
              (d/delete-database db-config))))
