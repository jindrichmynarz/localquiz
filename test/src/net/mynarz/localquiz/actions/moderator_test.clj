(ns net.mynarz.localquiz.actions.moderator-test
  (:require [net.mynarz.localquiz.actions.moderator :as moderator]
            [clojure.java.io :as io]
            [clojure.test :refer [are deftest]]))

(deftest parse-questions
  (are [questions-file key?] (let [questions (-> questions-file io/resource io/as-file)]
                               (key? (moderator/parse-questions-file questions)))
       "questions/empty.edn" :error
       "questions/questions.edn" :success))
