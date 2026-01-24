(ns net.mynarz.localquiz.actions.moderator
  (:require [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.question-sources :refer [question-sources]]
            [net.mynarz.localquiz.question-spec :as qs]
            [net.mynarz.localquiz.spec :as s]
            [net.mynarz.localquiz.util :as util]
            [net.mynarz.localquiz.views.common :refer [game-view]]
            [datahike.api :as d]
            [fast-edn.core :as edn]
            [taoensso.timbre :as log])
  (:import (java.io File)))

(defn parse-questions
  "Parse quiz questions from `questions-file`."
  [^File questions-file]
  (try
    (let [questions (edn/read-once questions-file)]
      (if-let [validation-report (s/validate ::qs/data questions)]
        {:error validation-report}
        {:success questions}))
    (catch Exception ex
      {:error (.getMessage ex)})))

(defn validate-questions
  "Validate the uploaded questions according to their spec."
  [{{{question-file :tempfile} "question-file"} :multipart-params
    :as request}]
  (let [{:keys [error]} (parse-questions question-file)]
    (game-view
      (cond-> request
        error (assoc :error error)))))

(defn create-game!
  "Create a game identified by `game-id`."
  [{{:keys [number-of-questions]
     question-source "question-source"
     :or {number-of-questions 20}} :form-params
    {{question-file :tempfile} "question-file"} :multipart-params
    game-id :sid
    :as request}]
  ; TODO: What should happen if the game already exists? Shall we recreate it?
  (let [{:keys [error success]} (cond
                                   question-file (parse-questions question-file)
                                   question-source (->> question-source
                                                        (get question-sources)
                                                        edn/read-once
                                                        (hash-map :success)))]
    (if error
      (-> request
          (assoc :error error)
          game-view)
      (let [questions (->> success
                           :questions
                           shuffle
                           (take number-of-questions)
                           (map (comp pr-str util/replace-react-fragments)))
            questions-total (-> questions
                                count
                                long)]
        (log/infof "Creating a new game %s." game-id)
        (d/transact db-conn [{:game/id game-id
                              :game/state :new
                              :game/questions questions
                              :game/questions-total questions-total}])))))

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
