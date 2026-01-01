(ns net.mynarz.localquiz.db
  (:require [datahike.api :as d]
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

(defn is-edn?
  [db eid]
  (let [questions (->> eid
                       (d/entity db)
                       :game/questions)]
    ; TODO: clojure.spec validation?
    true))
    ;(every? (fn [question] (map? question)) questions)))

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
   ;; {:db/ident :player/time-joined
   ;;  :db/doc "Time when the player joined the game"
   ;;  :db/valueType :db.type/instant
   ;;  :db/cardinality :db.cardinality/one}
   {:db/ident :player/score
    :db/doc "Player's score"
    :db/valueType :db.type/double
    :db/cardinality :db.cardinality/one}
   ;; {:db/ident :question/text
   ;;  :db/doc "Question in Hiccup"
   ;;  :db/valueType :db.type/string
   ;;  :db/cardinality :db.cardinality/one}
   ;; {:db/ident :question/note
   ;;  :db/doc "Optional note in Hiccup for a question"
   ;;  :db/valueType :db.type/string
   ;;  :db/cardinality :db.cardinality/one}
   {:db/ident :game
    :db.entity/attrs [:game/id :game/state :game/questions]
    :db.entity/preds ['net.mynarz.localquiz.db/is-edn?
                      'net.mynarz.localquiz.db/valid-game-state?]}
   {:db/ident :player
    :db.entity/attrs [:player/id
                      :player/name]}])
                      ;:player/time-joined]}])

(def config
  {:initial-tx schema
   :keep-history? true
   :schema-flexibility :write
   :store {:backend :mem
           :id "localquiz"}})

(defstate ^{:on-reload :noop} db-conn
  :start (do (d/create-database config)
             (d/connect config))
  :stop (do (d/release db-conn)
            (d/delete-database config)))
