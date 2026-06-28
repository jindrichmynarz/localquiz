(ns net.mynarz.localquiz.test-fixtures
  (:require [net.mynarz.localquiz.config]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.db :as db]
            [net.mynarz.localquiz.i18n :as i18n]
            [clojure.string :as string]
            [datahike.api :as d]
            [mount.core :as mount]
            [taoensso.timbre :as log]
            [taoensso.trove :as trove]
            [taoensso.trove.console :as trove-console]
            [taoensso.tempura :as tempura]))

(defonce game-id
  (crypto/random-unguessable-uid))

(defonce moderator-id
  (crypto/random-unguessable-uid))

(defonce question
  {})

(def initial-tx
  [{:game/id game-id
    :game/moderator moderator-id
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
    :db/ensure :game}
   {:session/id moderator-id
    :session/params (pr-str {})}])

(defn test-config
  [f]
  (mount/start #'net.mynarz.localquiz.config/config)
  (f)
  (mount/stop))

(defn configure-test-logging!
  "Datahike and konserve log through Trove (not Timbre), so the Timbre config below
  cannot quiet them. Install a Trove backend that drops their verbose (< :warn) logs
  and the :datahike/write-error noise emitted when tests intentionally trigger failed
  transactions (the CAS idempotency guard, joining an already-started game). Other
  warnings and errors still pass through to the console."
  []
  (let [log-fn (trove-console/get-log-fn)
        verbose? #{:trace :debug :info}]
    (trove/set-log-fn!
      (fn [ns coords level id lazy_]
        (when-not (or (= id :datahike/write-error)
                      (and (verbose? level)
                           (re-find #"^(datahike|konserve)" (str ns))))
          (log-fn ns coords level id lazy_))))))

(defn test-db
  [f]
  ; Filter Datahike's verbose logging
  (log/merge-config! {:min-level [[#{"datahike.*" "konserve.*"} :warn]]})
  (configure-test-logging!)
  (mount/start-with-args {})
  (d/transact db/db-conn initial-tx)
  (f)
  (mount/stop))

(def tr
  (partial tempura/tr {:dict i18n/dictionary} [:en]))
