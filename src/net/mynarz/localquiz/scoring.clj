(ns net.mynarz.localquiz.scoring
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.normalize :refer [normalize-answer]]
            [clj-fuzzy.jaro-winkler :refer [jaro-winkler]]))

(def boolean->score
  {true 1.0
   false 0.0})

(defmulti score-answers
  "Mark if given answers are correct for the given question by adding the boolean :correct? flag
  and give them numeric :score from <0, 1>."
  (fn [{:keys [type scoring]} _]
    (if (= scoring :consensus)
      [scoring]
      [type scoring])))

(defmethod score-answers [:multiple nil]
  [{:keys [choices]} answers]
  (for [answer answers
        :let [correct? (->> answer
                            :answer
                            (nth choices)
                            :correct?
                            boolean)]]
    (assoc answer :correct? correct?
                  :score (boolean->score correct?))))

(defmethod score-answers [:yesno nil]
  [{:keys [correct?]} answers]
  (for [answer answers
        :let [answer-correct? (= (:answer answer) correct?)]]
    (assoc answer :correct? answer-correct?
                  :score (boolean->score answer-correct?))))

(defmethod score-answers [:open nil]
  [question answers]
  (let [expected (-> question :answer normalize-answer)]
    (for [answer answers
          :let [actual (-> answer :answer str normalize-answer)
                correct? (> (jaro-winkler actual expected)
                            (:similarity-threshold config))]]
      (assoc answer :correct? correct?
                    :score (boolean->score correct?)))))

(defmethod score-answers [:percent-range nil]
  [{:keys [percentage threshold]
    :or {threshold 5}}
   answers]
  (for [answer answers
        :let [difference (Math/abs (- ^double (:answer answer) percentage))
              correct? (<= difference threshold)]]
    (assoc answer :correct? correct?
                  :score (if correct?
                           (- 1 (/ difference 100))
                           0.0))))

(defmethod score-answers [:sort nil]
  [{:keys [items]} answers]
  (let [expected (->> items
                      (map :sort-value)
                      (map vector (range))
                      (sort-by second)
                      (mapv first))]
    (for [answer answers
          :let [correct? (= (:answer answer) expected)]]
      (assoc answer :correct? correct?
                    :score (boolean->score correct?)))))

(defn consensus-scores
  "Build a map of answers to their scores based on consensus.
  No consensus gets the score of 0, complete consensus the score of 1."
  [answers]
  (let [answer-count (count answers)
        increment (if (> answer-count 1)
                    (double (/ 1 (dec answer-count)))
                    0)]
    (->> answers
        (reduce (fn [scores answer]
                  (let [answer-score (get scores answer)]
                    (assoc! scores
                            answer
                            (or (and answer-score (+ answer-score increment)) 0.0))))
                (transient {}))
        persistent!)))

(defmethod score-answers [:consensus]
  [_ answers]
  (let [answer->score (->> answers
                           (map :answer)
                           consensus-scores)]
    (for [answer answers]
      (assoc answer :score (-> answer
                               :answer
                               answer->score)))))

(defn scale-scores-by-answer-times
  "Scale `scores` by answer times."
  [scores]
  (if-let [answer-times (when-let [correct-times (->> scores
                                                      (filter (comp pos? :score))
                                                      (map :answer-time)
                                                      distinct
                                                      seq)]
                          (when (next correct-times) ; Don't scale if there's only 1 correct answer.
                            correct-times))]
    (let [min-answer-time (apply min answer-times)
          max-answer-time (apply max answer-times)
          time-range (- max-answer-time min-answer-time)]
      (if (pos? time-range)
        (for [{:keys [answer-time]
               :as score} scores
              :let [time-coefficient (+ 0.5 (* 0.5 (- 1 (/ (- answer-time min-answer-time) time-range))))]]
          (update score :score * time-coefficient))
        scores))
    scores))
