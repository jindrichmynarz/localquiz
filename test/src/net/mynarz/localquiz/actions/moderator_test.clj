(ns net.mynarz.localquiz.actions.moderator-test
  (:require [net.mynarz.localquiz.actions.moderator :as moderator]
            [clojure.java.io :as io]
            [clojure.test :refer [are deftest is]]))

(deftest parse-questions
  (are [questions-file key?] (let [questions (-> questions-file io/resource io/as-file)]
                               (is (key? (moderator/parse-questions-file questions))))
       "questions/empty.edn" :error
       "questions/questions.edn" :success))

(deftest sanitize-hiccup
  (are [hiccup normalized] (= (moderator/sanitize-hiccup hiccup) normalized)
       [:script] nil
       [:div [:script {:src "https://evil.com"}] "Foo"] [:div "Foo"]
       {:text [:script "alert(1)"]} {:text nil}))
