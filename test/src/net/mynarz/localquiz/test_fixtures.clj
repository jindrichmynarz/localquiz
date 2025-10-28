(ns net.mynarz.localquiz.test-fixtures
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.db :as db]
            [net.mynarz.localquiz.util :refer [now]]
            [datahike.api :as d]
            [mount.core :as mount]
            [taoensso.timbre :as log]))

(defonce game-id
  (crypto/random-unguessable-uid))

(defonce question
  {})

(def initial-tx
  [{:game/id game-id
    :game/questions [(pr-str question)]
    :game/players [{:player/id (crypto/random-unguessable-uid)
                    :player/name "Jane"
                    :player/score 1
                    :player/time-joined (now)
                    :db/ensure :player}
                   {:player/id (crypto/random-unguessable-uid)
                    :player/name "Bob"
                    :player/score 0
                    :player/time-joined (now)
                    :db/ensure :player}]
    :db/ensure :game}])

(defn test-db
  [f]
  ; Filter Datahike's verbose logging
  (log/merge-config! {:min-level [[#{"datahike.*" "konserve.*"} :warn]]})
  (let [test-db-config (update db/config :initial-tx into initial-tx)]
    (with-redefs [db/config test-db-config]
      (mount/start #'net.mynarz.localquiz.db/db-conn)))
  (f)
  (mount/stop))
