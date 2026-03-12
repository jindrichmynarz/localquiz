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
            [spec-tools.core :as st]
            [taoensso.timbre :as log])
  (:import (java.io File)))

(defn parse-questions-file
  "Parse quiz questions from `questions-file`."
  [^File questions-file]
  (try
    (let [questions (edn/read-once questions-file)]
      (if-let [validation-report (s/validate ::qs/data questions)]
        {:error validation-report}
        {:success questions}))
    (catch Exception ex
      {:error (.getMessage ex)})))

(defn parse-questions
  "Parse questions either from a selected question source or an uploaded question file."
  [{{question-source "question-source"} :form-params
    {{question-file :tempfile} "question-file"} :multipart-params
    :tempura/keys [tr]}]
  (cond
     question-source (->> question-source
                          (get question-sources)
                          edn/read-once
                          (hash-map :success))
     question-file (parse-questions-file question-file)
     :else {:error (tr [:errors/question-source-missing])}))

(defn validate-questions
  "Validate the uploaded questions according to their spec."
  [request]
  (let [{:keys [error]
         {:keys [questions]} :success} (parse-questions request)]
    (game-view
      (if error
        (assoc request :error error)
        (assoc request :success {:number-of-questions (count questions)})))))

(defn create-game!
  "Create a game identified by `game-id`."
  [{game-id :sid
    :keys [form-params multipart-params]
    :as request}]
  ; TODO: What should happen if the game already exists? Shall we recreate it?
  (let [number-of-questions (or (st/coerce ::s/number-of-questions
                                           (or (get form-params "number-of-questions")
                                               (get multipart-params "number-of-questions"))
                                           st/string-transformer)
                                20)
        {:keys [error success]} (parse-questions request)]
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
