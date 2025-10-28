(ns net.mynarz.localquiz.actions.moderator-test
  (:require [net.mynarz.localquiz.actions.moderator :as moderator]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [deftest is use-fixtures]]
            [datahike.api :as d]))

(defn db-empty?
  "Test if the database is empty."
  []
  (->> @db-conn
       (d/q '[:find ?e ?a ?v
              :where [?e ?a ?v]
              (not (or [?e :db/ident _]))]) ; Exclude schema entities which all have :db/ident.
       empty?))

(use-fixtures :each fixtures/test-db)

(deftest next-question!
  (is (= (moderator/next-question! fixtures/game-id) fixtures/question))
  (is (nil? (moderator/next-question! fixtures/game-id))))

(deftest end-game!
  (moderator/end-game! fixtures/game-id)
  (is (db-empty?)))
