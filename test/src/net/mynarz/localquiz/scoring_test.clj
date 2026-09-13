(ns net.mynarz.localquiz.scoring-test
  (:require [net.mynarz.localquiz.scoring :as scoring]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [clojure.test :refer [are deftest is testing use-fixtures]]))

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

(deftest malformed-answers-are-isolated
  ;; A malformed answer (out-of-range choice index, non-numeric percent) scores 0
  ;; instead of throwing and aborting the whole batch.
  (are [question answers scores] (= (map :score (scoring/score-answers question answers)) scores)
       {:type :multiple
        :choices [{:correct? true} {}]}
       [{:answer 0}
        {:answer 99}
        {:answer "x"}]
       [1.0 0.0 0.0]

       {:type :percent-range
        :percentage 50}
       [{:answer 48}
        {:answer "not-a-number"}]
       [0.98 0.0]))

(deftest consensus-scoring
  (are [answers scores] (= (map :score (scoring/score-answers {:scoring :consensus} answers)) scores)
       [{:answer 1} {:answer 1} {:answer 3}]
       [0.5 0.5 0.0]

       [{:answer false} {:answer true}]
       [0.0 0.0]

       [{:answer 1} {:answer 1}]
       [1.0 1.0])
  (testing "Consensus is measured among all players, answering or not"
    (is (= [0.5 0.5]
           (map :score (scoring/score-answers {:scoring :consensus :player-count 3}
                                              [{:answer 1} {:answer 1}]))))))

(deftest network-consensus-scoring
  ; root -> A, B; A -> A1, A2; B -> B1
  (let [question {:type :network
                  :scoring :consensus
                  :choices [{:label "A" :related ["root"]}
                            {:label "B" :related ["root"]}
                            {:label "A1" :related ["A"]}
                            {:label "A2" :related ["A"]}
                            {:label "B1" :related ["B"]}]}
        scores (fn [& answers]
                 (map :score (scoring/score-answers question (map #(hash-map :answer %) answers))))
        close? (fn [xs ys]
                 (and (= (count xs) (count ys))
                      (every? true? (map #(< (abs (- %1 %2)) 1e-9) xs ys))))]
    (testing "An exact match adds 1/(n - 1), and a sibling two hops away a third of that"
      (is (close? [(/ 2 3) (/ 2 3) (/ 1 3)] (scores "A1" "A1" "A2"))))
    (testing "A parent is one hop away, so it adds a half"
      (is (close? [0.5 0.5] (scores "A" "A1"))))
    (testing "The root, which is no choice, links no answers"
      (is (close? [0.0 0.0] (scores "A" "B"))))
    (testing "Agreement scores 1.0 at most"
      (is (close? [1.0 1.0] (scores "A1" "A1"))))
    (testing "Answers more than two hops apart credit nothing"
      (is (close? [0.0 0.0] (scores "A1" "B1"))))
    (testing "An answer that is no node only matches exactly"
      (is (close? [0.0 0.0] (scores "A1" "bogus"))))
    (testing "A lone answer has none to agree with"
      (is (close? [0.0] (scores "A1"))))
    (testing "Players who did not answer count too"
      (is (close? [0.5 0.5]
                  (map :score (scoring/score-answers (assoc question :player-count 3)
                                                     [{:answer "A1"} {:answer "A1"}])))))))

(deftest majority-scoring
  (are [answers scores] (= (map :score (scoring/score-answers {:scoring :majority} answers)) scores)
       [{:answer 1} {:answer 2}]
       [0.0 0.0]

       [{:answer 1} {:answer 0} {:answer 1}]
       [1.0 0.0 1.0]

       [{:answer 1} {:answer 0}]
       [0.0 0.0]

       [{:answer 1} {:answer 1} {:answer 1}]
       [1.0 1.0 1.0]

       [{:answer 1} {:answer 1} {:answer 2} {:answer 2}]
       [0.0 0.0 0.0 0.0]

       [{:answer 1} {:answer 1} {:answer 2} {:answer 3}]
       [0.0 0.0 0.0 0.0]

       [{:answer 1} {:answer 1} {:answer 1} {:answer 2} {:answer 2}]
       [1.0 1.0 1.0 0.0 0.0]

       [{:answer false} {:answer false} {:answer true}]
       [1.0 1.0 0.0]

       [{:answer 1} {:answer 1} {:answer nil}]
       [1.0 1.0 0.0]

       [{:answer 1} {:answer 1} {:answer nil} {:answer nil}]
       [0.0 0.0 0.0 0.0]))

(deftest scale-scores-by-answer-times
  (are [scores scaled-scores] (= (map :score (scoring/scale-scores-by-answer-times scores)) scaled-scores)
       [{:score 1.0 :answer-time 10} {:score 1.0 :answer-time 20}]
       [1.0 0.5]

       []
       []

       [{:score 0.0 :answer-time 10} {:score 1.0 :answer-time 50}]
       [0.0 1.0]))
