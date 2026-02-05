(ns net.mynarz.localquiz.actions.moderator-test
  (:require [net.mynarz.localquiz.db :as db]
            [net.mynarz.localquiz.actions.moderator :as moderator]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is use-fixtures]]
            [datahike.api :as d]
            [ring.mock.request :as mock]
            [taoensso.timbre :as log]))

(use-fixtures :each fixtures/test-db)

(defn db-empty?
  "Test if the database is empty."
  []
  (->> @db/db-conn
       (d/q '[:find ?e ?a ?v
              :where [?e ?a ?v]
              (not (or [?e :db/ident _] ; Exclude schema entities which all have :db/ident.
                       [?e :db/txInstant _]))])
       empty?))

(defn game-deleted?
  [^String game-id]
  (->> game-id
       (d/q '[:find (pull ?game [*])
              :in $ ?game-id
              :where [?game :game/id ?game-id]]
            @db/db-conn)
       seq
       not))

;; (deftest validate-questions
;;   (with-open [questions (-> "questions/empty.edn" io/resource io/input-stream)]
;;      (is (-> (mock/request :post "/create/validate")
;;              (mock/multipart-body {"question-file" {:value questions}})
;;              (assoc :tempura/tr fixtures/tr)
;;              moderator/validate-questions
;;              log/spy
;;              :error))))

(deftest end-game!
  (moderator/end-game! {:sid fixtures/game-id})
  (is (game-deleted? fixtures/game-id))
  (is (db-empty?)))
