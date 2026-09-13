(ns net.mynarz.localquiz.scoring
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.network :as network]
            [net.mynarz.localquiz.normalize :refer [normalize-answer]]
            [clj-fuzzy.jaro-winkler :refer [jaro-winkler]]))

(def boolean->score
  {true 1.0
   false 0.0})

(defmulti score-answers
  "Mark if given answers are correct for the given question by adding the boolean :correct? flag
  and give them numeric :score from <0, 1>."
  (fn [{:keys [type scoring]} _]
    (cond
      ; Consensus on a network credits near answers too.
      (= [type scoring] [:network :consensus]) [:network :consensus]
      (#{:consensus :majority} scoring) [scoring]
      :else [type scoring])))

(defn- score-each
  "Map per-answer scoring `f` over `answers`, isolating failures: if `f` throws for an
  answer (e.g. a malformed value such as an out-of-range choice index), that answer is
  marked incorrect with a zero score instead of aborting the whole batch."
  [f answers]
  (map (fn [answer]
         (try
           (f answer)
           (catch Exception _
             (assoc answer :correct? false :score 0.0))))
       answers))

(defmethod score-answers [:multiple nil]
  [{:keys [choices]} answers]
  (score-each
    (fn [answer]
      (let [correct? (->> answer
                          :answer
                          (nth choices)
                          :correct?
                          boolean)]
        (assoc answer :correct? correct?
                      :score (boolean->score correct?))))
    answers))

(defmethod score-answers [:yesno nil]
  [{:keys [correct?]} answers]
  (score-each
    (fn [answer]
      (let [answer-correct? (= (:answer answer) correct?)]
        (assoc answer :correct? answer-correct?
                      :score (boolean->score answer-correct?))))
    answers))

(defmethod score-answers [:open nil]
  [question answers]
  (let [expected (-> question :answer normalize-answer)]
    (score-each
      (fn [answer]
        (let [actual (-> answer :answer str normalize-answer)
              correct? (> (jaro-winkler actual expected)
                          (:similarity-threshold config))]
          (assoc answer :correct? correct?
                        :score (boolean->score correct?))))
      answers)))

(defmethod score-answers [:percent-range nil]
  [{:keys [percentage threshold]
    :or {threshold 5}}
   answers]
  (score-each
    (fn [answer]
      (let [difference (Math/abs (- ^double (:answer answer) percentage))
            correct? (<= difference threshold)]
        (assoc answer :correct? correct?
                      :score (if correct?
                               (- 1 (/ difference 100))
                               0.0))))
    answers))

(defmethod score-answers [:sort nil]
  [{:keys [items]} answers]
  (let [expected (->> items
                      (map :sort-value)
                      (map vector (range))
                      (sort-by second)
                      (mapv first))]
    (score-each
      (fn [answer]
        (let [correct? (= (:answer answer) expected)]
          (assoc answer :correct? correct?
                        :score (boolean->score correct?))))
      answers)))

(defn consensus-scores
  "Build a map of answers to their scores based on consensus among `player-count` players,
  answering or not. No consensus gets the score of 0, complete consensus the score of 1."
  [answers
   ^long player-count]
  (let [increment (if (> player-count 1)
                    (double (/ 1 (dec player-count)))
                    0)]
    (->> answers
        (reduce (fn [scores answer]
                  (let [answer-score (get scores answer)]
                    (assoc! scores
                            answer
                            (or (and answer-score (+ answer-score increment)) 0.0))))
                (transient {}))
        persistent!)))

(defn- player-count
  "Players in the game of `question`, answering or not, which consensus is measured among.
  Without the count, only the answering ones."
  [question answers]
  (or (:player-count question) (count answers)))

(defmethod score-answers [:consensus]
  [question answers]
  (let [answer->score (consensus-scores (map :answer answers)
                                        (player-count question answers))]
    (for [answer answers]
      (assoc answer :score (-> answer
                               :answer
                               answer->score)))))

(def ^:private max-hops
  "Farthest apart that two answers to a :network question may be to credit each other."
  2)

(defmethod score-answers [:network :consensus]
  ; Each other answer within `max-hops` of this one adds 1/((d + 1)(n - 1)), d being the
  ; hops between them and n the number of players, answering or not. An exact match adds
  ; 1/(n - 1), so the score still peaks at 1.0, when all agree, and with only exact matches
  ; it is plain consensus.
  [{:keys [choices]
    :as   question}
   answers]
  (let [others    (dec (player-count question answers))
        answered  (frequencies (map :answer answers))
        score     (memoize
                    (fn [value]
                      (if (pos? others)
                        (let [near (network/nearby choices value max-hops)]
                          (/ (reduce + (for [[other n] answered
                                             :let  [hops (near other)]
                                             :when hops]
                                         ; Not this answer itself.
                                         (/ (if (= other value) (dec n) n) (inc hops))))
                             (double others)))
                        0.0)))]
    (score-each #(assoc % :score (score (:answer %))) answers)))

(defmethod score-answers [:majority]
  [_ answers]
  (let [majority-threshold (/ (count answers) 2)
        majority-entry (->> answers
                            (keep :answer)
                            frequencies
                            (filter (comp (partial < majority-threshold) val))
                            first)]
    (for [answer answers]
      (assoc answer :score (if (and majority-entry (= (:answer answer) (key majority-entry)))
                             1.0
                             0.0)))))

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
