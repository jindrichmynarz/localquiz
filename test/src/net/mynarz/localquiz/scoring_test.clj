(ns net.mynarz.localquiz.scoring-test
  (:require [net.mynarz.localquiz.scoring :as scoring]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [are deftest use-fixtures]]))

(use-fixtures :once fixtures/test-config)

(deftest score-answers
  (are [question answers scores] (= (map :score (scoring/score-answers question answers)) scores)
       {:type :multiple
        :choices [{:correct? true} {} {} {}]}
       [{:answer 0}
        {:answer 3}
        {:answer 0}]
       [1.0 0.0 1.0]

       {:type :yesno
        :correct? false}
       [{:answer false}
        {:answer true}
        {:answer false}]
       [1.0 0.0 1.0]

       {:type :sort
        :items [{:sort-value 3} {:sort-value 1} {:sort-value 4} {:sort-value 2}]}
       [{:answer [1 3 0 2]}
        {:answer [0 1 2 3]}]
       [1.0 0.0]

       {:type :open
        :answer "Jako doma"}
       [{:answer "Jako doma"}
        {:answer "JAKO DOMA"}
        {:answer "Jákô dóma"}]
       [1.0 1.0 1.0]))

(deftest consensus-scoring
  (are [answers scores] (= (map :score (scoring/score-answers {:scoring :consensus} answers)) scores)
       [{:answer 1} {:answer 1} {:answer 3}]
       [0.5 0.5 0.0]

       [{:answer false} {:answer true}]
       [0.0 0.0]

       [{:answer 1} {:answer 1}]
       [1.0 1.0]))

(deftest scale-scores-by-answer-times
  (are [scores scaled-scores] (= (map :score (scoring/scale-scores-by-answer-times scores)) scaled-scores)
       [{:score 1.0 :answer-time 10} {:score 1.0 :answer-time 20}]
       [1.0 0.5]

       []
       []

       [{:score 0.0 :answer-time 10} {:score 1.0 :answer-time 50}]
       [0.0 1.0]))
