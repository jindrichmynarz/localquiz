(ns net.mynarz.localquiz.db-listener-test
  (:require [net.mynarz.localquiz.async :as async]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.db-listener :as db-listener]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.core.async :as a]
            [clojure.test :refer [are deftest is testing use-fixtures]]
            [datahike.api :as d]))

(use-fixtures :each fixtures/test-db)

(deftest find-updated-sessions
  (let [player-ids (d/q '[:find [?player-id ...]
                          :in $ ?game-id
                          :where [?game :game/id ?game-id]
                                 [?game :game/players ?player]
                                 [?player :player/id ?player-id]]
                         @db-conn
                         fixtures/game-id)
        session-ids (-> player-ids
                        set
                        (conj fixtures/moderator-id))]
    ;; Datahike 0.8 no longer reports datoms whose value is already present, so every
    ;; transaction below has to change something for it to show up in the tx-report.
    (testing "Game updates"
      (are [tx-data] (let [tx-report (d/transact db-conn tx-data)]
                       (is (= (set (db-listener/find-updated-sessions tx-report)) session-ids)))
        [{:player/id (first player-ids)
          :player/score 42.0}]
        [{:game/id fixtures/game-id
          :game/state :question}]))

    (testing "Component updates"
      (let [tx-report (d/transact db-conn [{:game/id fixtures/game-id
                                            :game/answers [{:answer/player [:player/id (first player-ids)]
                                                            :answer/answer "0"}]}])]
        (is (= (set (db-listener/find-updated-sessions tx-report)) session-ids))))

    (testing "Session update"
      (let [session-id (first session-ids)
            tx-report (d/transact db-conn [{:session/id session-id
                                            :session/search-fragment "amb"}])]
        (is (= (set (db-listener/find-updated-sessions tx-report)) #{session-id}))))

    (testing "Retraction updates"
      (let [session-id (first session-ids)
            tx-report (d/transact db-conn [[:db/retractEntity [:session/id session-id]]])]
        (is (= (set (db-listener/find-updated-sessions tx-report)) #{session-id})))
      ;; The player who leaves is refreshed too.
      (let [tx-report (d/transact db-conn [[:db/retractEntity [:player/id (second player-ids)]]])]
        (is (= (set (db-listener/find-updated-sessions tx-report)) session-ids)))
      (let [tx-report (d/transact db-conn [[:db/retractEntity [:game/id fixtures/game-id]]])]
        (is (= (set (db-listener/find-updated-sessions tx-report))
               (disj session-ids (second player-ids))))))))

(deftest refresher
  (let [<refresh (a/sub async/refresh-pub [fixtures/moderator-id :render] (a/chan 1))]
    (d/transact db-conn [{:game/id fixtures/game-id
                          :game/state :question}])
    (is (= {:session-id fixtures/moderator-id}
           (a/alt!! <refresh ([v] v)
                    (a/timeout 1000) :timeout)))))
