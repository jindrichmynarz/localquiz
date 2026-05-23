(ns net.mynarz.localquiz.actions.moderator
  (:require [net.mynarz.localquiz.actions.common :refer [refresh-event!]]
            [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.question-sources :refer [question-sources]]
            [net.mynarz.localquiz.question-spec :as qs]
            [net.mynarz.localquiz.spec :as s]
            [net.mynarz.localquiz.util :as util]
            [clojure.java.io :as io]
            [fast-edn.core :as edn])
  (:import (java.io File)))

(defn parse-questions-file
  "Parse quiz questions from `questions-file`."
  [^File questions-file]
  (try
    (let [data (edn/read-once {:readers {}} ; Disable readers for security
                              questions-file)
          resolved (qs/resolve-refs (:defs data) data)]
      (if-let [validation-report (s/validate ::qs/data resolved)]
        {:error validation-report}
        {:success data}))
    (catch clojure.lang.ExceptionInfo ex
      {:error (.getMessage ex)})
    (catch Exception ex
      {:error (.getMessage ex)})))

(defn parse-questions-resource
  "Parse questions resource from `resource-url`."
  [tr
   ^String resource-url]
  (if (->> question-sources
           vals
           (apply concat)
           (some (comp #{resource-url} :url)))
    (with-open [input-stream (-> resource-url io/resource io/input-stream)]
      (let [data (edn/read-once input-stream)]
        (qs/resolve-refs (:defs data) data)
        {:success data}))
    {:error (tr [:errors/unknown-question-source])}))

(defn parse-questions
  "Parse questions either from a selected question source or an uploaded question file."
  [{{{:keys [question-source]} :form
     {{question-file :tempfile} :question-file} :multipart} :parameters
    :tempura/keys [tr]}]
  (cond
     question-source (parse-questions-resource tr question-source)
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
    (refresh-event! game-id game-id {:signals signals})))

(def serialize
  (comp pr-str util/replace-react-fragments))

(defn create-game!
  "Create a game identified by `game-id`."
  [{{:keys [form multipart]} :parameters
    game-id :sid
    :as request}]
  ; TODO: What should happen if the game already exists? Shall we recreate it?
  (let [number-of-questions (or (:number-of-questions form)
                                (:number-of-questions multipart)
                                (:default-number-of-questions config))
        {:keys [error success]} (parse-questions request)]
    (if error
      (refresh-event! game-id game-id {:signals {:error error}})
      (let [defs (mapv (fn [[id value]]
                         {:def/id id
                          :def/value (serialize value)})
                       (:defs success))]
        (game/create-game! game-id
                           (->> success
                                :questions
                                shuffle
                                (take number-of-questions)
                                (map serialize))
                           defs)))))

(defn next!
  [{game-id :sid}]
  (game/advance! game-id))

(defn end-game!
  [{game-id :sid}]
  (game/end-game! game-id))
