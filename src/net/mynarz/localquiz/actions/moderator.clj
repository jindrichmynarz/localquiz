(ns net.mynarz.localquiz.actions.moderator
  (:require [net.mynarz.localquiz.actions.common :refer [refresh-signals!]]
            [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.question-sources :refer [question-sources]]
            [net.mynarz.localquiz.question-spec :as qs]
            [net.mynarz.localquiz.spec :as s]
            [net.mynarz.localquiz.util :as util]
            [fast-edn.core :as edn])
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
  [{{{:keys [question-source]} :form
     {{question-file :tempfile} :question-file} :multipart} :parameters
    :tempura/keys [tr]}]
  (cond
     question-source (->> question-source
                          (get question-sources)
                          (edn/read-once {:readers {}}) ; Disable readers
                          (hash-map :success))
     question-file (parse-questions-file question-file)
     :else {:error (tr [:errors/question-source-missing])}))

(defn validate-questions!
  "Validate the uploaded questions according to their spec."
  [{game-id :sid
    :as request}]
  (let [{:keys [error]
         {:keys [questions]} :success} (parse-questions request)
        signals (if error
                  {:error error}
                  {:error false
                   :numberOfQuestions (count questions)})]
    (refresh-signals! game-id game-id signals)))

(defn create-game!
  "Create a game identified by `game-id`."
  [{{{:keys [number-of-questions]} :form} :parameters
    game-id :sid
    :as request}]
  ; TODO: What should happen if the game already exists? Shall we recreate it?
  (let [{:keys [error success]} (parse-questions request)]
    (if error
      (refresh-signals! game-id game-id {:error error})
      (game/create-game! game-id (->> success
                                      :questions
                                      shuffle
                                      (take (or number-of-questions (:default-number-of-questions config)))
                                      (map (comp pr-str util/replace-react-fragments)))))))

(defn leaderboard!
  [{game-id :sid}]
  (game/leaderboard! game-id))

(defn next-question!
  [{game-id :sid}]
  (game/next-question! game-id))

(defn end-game!
  [{game-id :sid}]
  (game/end-game! game-id))
