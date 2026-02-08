(ns net.mynarz.localquiz.test-fixtures
  (:require [net.mynarz.localquiz.async :as async]
            [net.mynarz.localquiz.config :as config]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.db :as db]
            [net.mynarz.localquiz.i18n :as i18n]
            [net.mynarz.localquiz.question-sources :as question-sources]
            [datahike.api :as d]
            [mount.core :as mount]
            [taoensso.timbre :as log]
            [taoensso.tempura :as tempura]))

(defonce game-id
  (crypto/random-unguessable-uid))

(defonce question
  {})

(def initial-tx
  [{:game/id game-id
    :game/state :new
    :game/questions [(pr-str question)]
    :game/players [{:player/id (crypto/random-unguessable-uid)
                    :player/name "Jane"
                    :player/score 1.0
                    :db/ensure :player}
                   {:player/id (crypto/random-unguessable-uid)
                    :player/name "Bob"
                    :player/score 0.0
                    :db/ensure :player}]
    :db/ensure :game}])

(defn test-db
  [f]
  ; Filter Datahike's verbose logging
  (log/merge-config! {:min-level [[#{"datahike.*" "konserve.*"} :warn]]})
  (mount/start #'net.mynarz.localquiz.config/config
               #'net.mynarz.localquiz.db/db-conn
               #'net.mynarz.localquiz.question-sources/question-sources
               #'net.mynarz.localquiz.async/refresh-channel
               #'net.mynarz.localquiz.async/refresh-pub)
  (d/transact db/db-conn initial-tx)
  (f)
  (mount/stop))

(def tr
  (partial tempura/tr {:dict i18n/dictionary} [:en]))
