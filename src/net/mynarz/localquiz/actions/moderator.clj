(ns net.mynarz.localquiz.actions.moderator
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :as util]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

(defn pick-questions!
  [{game-id :sid
    :as request}])
  ; TODO
  ; - Destructure POST parameter "questions-upload"
  ; - Transact the questions to the database

(defn create-game!
  "Create a game identified by `game-id`."
  [{game-id :sid}]
  ; TODO: What should happen if the game already exists? Shall we recreate it?
  (let [questions (->> "questions/femquiz.edn"
                        util/read-edn-resource
                        :questions
                        shuffle
                        (take 20)
                        (map (comp pr-str util/replace-react-fragments)))]
    (log/infof "Creating a new game %s." game-id)
    (d/transact db-conn [{:game/id game-id
                          :game/state :new
                          :game/questions questions}])))

(defn leaderboard!
  [{game-id :sid}]
  (d/transact db-conn [[:db/add [:game/id game-id] :game/state :leaderboard]]))

(defn next-question! ; TODO: Think of a better separation between `actions` and the `game` namespaces.
  [{game-id :sid}]
  (game/next-question! game-id))

(defn end-game!
  [{game-id :sid}]
  (log/infof "Ending the game %s." game-id)
  (d/transact db-conn [[:db/retractEntity [:game/id game-id]]]))
