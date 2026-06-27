(ns net.mynarz.localquiz.actions.moderator
  (:require [net.mynarz.localquiz.actions.common :refer [refresh-session!]]
            [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.question-sources :refer [question-sources]]
            [net.mynarz.localquiz.question-spec :as qs]
            [net.mynarz.localquiz.sanitize :as sanitize]
            [net.mynarz.localquiz.spec :as s]
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
  [request]
  (let [{:keys [error]
         {:keys [questions]} :success} (parse-questions request)
        signals (if error
                  {:error error
                   :errorPreformatted true
                   :questionsValidated false}
                  {:error false
                   :questionsValidated true
                   :numberOfQuestions (count questions)})]
    (refresh-session! request {:signals signals})))

(defn create-game!
  "Create a game with a fresh, random public ID, owned by the requesting session."
  [{{:keys [form multipart]} :parameters
    sid :sid
    :as request}]
  (let [number-of-questions (or (:number-of-questions form)
                                (:number-of-questions multipart)
                                (:default-number-of-questions config))
        {:keys [error success]} (parse-questions request)]
    (if error
      (refresh-session! request {:signals {:error error
                                           :errorPreformatted true}})
      (let [game-id (crypto/random-unguessable-uid)
            defs (mapv (fn [[id value]]
                         {:def/id id
                          :def/value (pr-str (sanitize/sanitize-hiccup value))})
                       (:defs success))]
        (game/create-game! game-id
                           sid
                           (->> success
                                :questions
                                shuffle
                                (take number-of-questions)
                                (map (comp pr-str sanitize/sanitize-hiccup)))
                           defs)
        (refresh-session! request {:redirect (str "/host/" game-id)})))))

(defn next!
  [{{:keys [game-id]} :path-params
    sid :sid}]
  (when (game/moderator? game-id sid)
    (game/advance! game-id)))

(defn end-game!
  [{{:keys [game-id]} :path-params
    sid :sid}]
  (when (game/moderator? game-id sid)
    (game/end-game! game-id)))
