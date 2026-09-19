(ns net.mynarz.localquiz.actions.moderator-test
  (:require [net.mynarz.localquiz.actions.moderator :as moderator]
            [clojure.java.io :as io]
            [clojure.test :refer [are deftest]]))

(deftest parse-questions
  (are [questions-file key?] (let [questions (-> questions-file io/resource io/as-file)]
                               (key? (moderator/parse-questions-file questions)))
       "questions/empty.edn" :error
       "questions/questions.edn" :success
       "questions/network.edn" :success
       ; The :related of a network may loop,
       "questions/network_cycle.edn" :success
       ; but every node must be reachable from the root.
       "questions/network_unreachable.edn" :error))
