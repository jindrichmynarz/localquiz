(ns net.mynarz.localquiz.game
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.normalize :refer [normalize-answer]]
            [clj-fuzzy.jaro-winkler :refer [jaro-winkler]]
            [clojure.string :as string]
            [datahike.api :as d]
            [fast-edn.core :as edn]
            [taoensso.timbre :as log]))

(defn get-game-state
  "Get game state for the given `game-id`."
  [^String game-id]
  (d/q '[:find ?state .
         :in $ ?game-id
         :where [?game :game/id ?game-id]
                [?game :game/state ?state]]
       @db-conn
       game-id))

(defn player-in-game?
  "Test if the player with `player-id` is in the game with `game-id`."
  [^String game-id
   ^String player-id]
  (d/q '[:find ?player-id .
         :in $ ?game-id ?player-id
         :where [?game :game/id ?game-id]
                [?game :game/players ?player]
                [?player :player/id ?player-id]]
       @db-conn
       game-id
       player-id))

(defn player-name-in-game?
  "Test if a player with `player-name` is already in the game identified by `game-id`.
  Uses case-insensitive matching."
  [^String game-id
   ^String player-name]
  (let [query '[:find ?player
                :in $ ?game-id ?player-name
                :where [?game :game/id ?game-id]
                       [?game :game/players ?player]
                       [?player :player/name ?original-player-name]
                       [(.toLowerCase ^String ?original-player-name) ?lowercase-player-name]
                       [(= ?player-name ?lowercase-player-name)]]]
    (->> player-name
         string/lower-case
         (d/q query @db-conn game-id)
         seq
         some?)))

(defn player-name-valid-length?
  "Test if `player-name` is between 1 and 20 characters."
  [^String player-name]
  (< 1 (count player-name) 20))

(defn validate-player-name
  [^String game-id
   ^String player-name]
  (cond
    (player-name-in-game? game-id player-name) :errors.player-name/taken
    (not (player-name-valid-length? player-name)) :errors.player-name/length))

(defn lobby
  "Get the players waiting in the lobby for the game identified by `game-id`.
  The players are sorted in the chronological order according to when they joined the game."
  [^String game-id]
  (->> game-id
       (d/q '[:find ?player-name ?time-joined
              :keys player-name time-joined
              :in $ ?game-id
              :where [?game :game/id ?game-id]
                     [?game :game/players ?player ?player-tx]
                     [?player-tx :db/txInstant ?time-joined]
                     [?player :player/name ?player-name]]
            @db-conn)
       (sort-by :time-joined)
       (map :player-name)))

(defn current-question
  "Get the current question for `game-id`."
  [^String game-id]
  (some->> game-id
           (d/q '[:find ?current-question .
                  :in $ ?game-id
                  :where [?game :game/id ?game-id]
                         [?game :game/current-question ?current-question]]
                @db-conn)
           edn/read-string))

(defn parse-answer
  ; TODO: Shall we just use edn/read-string?
  [^String answer]
  (case answer
    nil nil
    "true" true
    "false" false
    (cond
      (re-matches #"^\d+$" answer) (Integer/parseInt answer)
      (re-matches #"^\d+\.\d+$" answer) (Double/parseDouble answer)
      (re-matches #"^\[(\d+(,\s*)?)+\]$" answer) (edn/read-string answer)
      :else answer)))

(defn player-answered?
  "Test if a player with `player-id` has already answered
  the current question in game with `game-id`."
  [^String player-id]
  (d/q '[:find ?player-id .
         :in $ ?player-id
         :where [?player :player/id ?player-id]
                [?answer :answer/player ?player]]
       @db-conn
       player-id))

(defn get-answers
  [^String game-id]
  (->> game-id
       (d/q '[:find ?player ?answer-entity ?answer ?answer-time
              :in $ ?game-id
              :keys player answer-entity answer answer-time
              :where [?game :game/id ?game-id]
                     [?game :game/players ?player]
                     [?game :game/current-question _ ?question-tx]
                     [?game :game/answers ?answer-entity ?answer-tx]
                     [?answer-entity :answer/player ?player]
                     [?question-tx :db/txInstant ?question-inst]
                     [(.getTime ?question-inst) ?question-added]
                     [?answer-tx :db/txInstant ?answer-inst]
                     [(.getTime ?answer-inst) ?answer-added]
                     [(- ?answer-added ?question-added) ?answer-time]
                     [?answer-entity :answer/answer ?answer]]
            @db-conn)
       (map (fn [result] (update result :answer parse-answer)))))

(def boolean->score
  {true 1.0
   false 0.0})

(defmulti score-answers (fn [{:keys [type scoring]} _] [type scoring]))

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
          :let [actual (-> answer :answer normalize-answer)
                correct? (> (jaro-winkler actual expected)
                            (:similarity-threshold config))]]
      (assoc answer :correct? correct?
                    :score (boolean->score correct?)))))

(defmethod score-answers [:percent-range nil]
  [{:keys [percentage]} answers]
  (for [answer answers
        :let [difference (Math/abs (- ^double (:answer answer) percentage))
              correct? (<= difference 5)]] ; TODO: Allow to configure tolerated difference?
    (assoc answer :correct? correct?
                  :score (- 1 (/ difference 100)))))

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

(defn consensus-scoring
  [answers]
  (let [answer->score (->> answers
                           (map :answer)
                           frequencies)]
    (for [answer answers]
      (assoc answer :score (- (answer->score (:answer answer)) 1.0)))))

(defmethod score-answers [:multiple :consensus]
  [_ answers]
  (consensus-scoring answers))

(defmethod score-answers [:yesno :consensus]
  [_ answers]
  (consensus-scoring answers))

(defn scale-scores-by-answer-times
  [scores]
  (if-let [max-answer-time (some->> scores
                                    (keep :answer-time)
                                    seq
                                    (apply max))]
    (for [score scores
          ; TODO: Think of less penalizing scaling.
          :let [time-modifier (- 1 (/ (:answer-time score) max-answer-time))]]
      (update score :score * time-modifier))
    scores))

(defn add-score
  "A transaction function that adds `answer-score` to the current score of the `player`."
  [db
   ^long player
   ^long answer-score]
  (d/q '[:find ?player ?score
         :in $ ?player ?answer-score
         :keys db/id player/score
         :where [(get-else $ ?player :player/score 0.0) ?current-score]
                [(+ ?current-score ?answer-score) ?score]]
       db
       player
       answer-score))

(defn store-scores
  [scores]
  (reduce (fn [acc {:keys [answer-entity correct? score]}]
            (cond-> (conj acc [:db/add answer-entity :answer/score score])
              (some? correct?) (conj [:db/add answer-entity :answer/correct? correct?])))
          []
          scores))

(defn add-scores
  "Create transaction data from `scores` to add them to the player scores."
  [scores]
  (mapv (fn [{:keys [player score]}]
          [:db.fn/call add-score player score])
        scores))

(defn descending-order
  "Sort `a` and `b` in the descending order."
  [a b]
  (compare b a))

(defn leaderboard
  "Get the player leaderboard for `game-id` using the database `conn`."
  [^String game-id]
  (->> game-id
       (d/q '[:find ?player-id ?player-name ?score
              :in $ ?game-id
              :keys player-id player-name score
              :where [?game :game/id ?game-id]
                     [?game :game/players ?player]
                     [?player :player/id ?player-id]
                     [?player :player/name ?player-name]
                     [(get-else $ ?player :player/score 0.0) ?score]]
            @db-conn)
       (sort-by :score descending-order)))

(defn winner
  "Get the ID of the winning player."
  [^String game-id]
  (-> game-id
      leaderboard
      first
      :player-id))

(defn has-enough-players?
  "Test if the game with `game-id` has at least 2 players."
  [^String game-id]
  (->> game-id
       (d/q '[:find ?player
              :in $ ?game-id
              :where [?game :game/id ?game-id]
                     [?game :game/players ?player]]
            @db-conn)
       count
       (< 1)))

(defn all-players-answered?
  "Test if all players in `game-id` answered the current question."
  [^String game-id]
  (->> game-id
       (d/q '[:find ?player
              :in $ ?game-id
              :where [?game :game/id ?game-id]
                     [?game :game/players ?player]
                     (not [?game :game/answers ?answer]
                          [?answer :answer/player ?player])]
            @db-conn)
       seq
       not))

(defn all-questions-answered?
  "Test if all questions in `game-id` were answered."
  [^String game-id]
  (->> game-id
       (d/q '[:find ?question .
              :in $ ?game-id
              :where [?game :game/id ?game-id]
                     [?game :game/questions ?question]]
            @db-conn)
       nil?))

(defonce timeouts
  (atom {}))

(defn cancel-timeout!
  [^String game-id
   timeouts]
  (if-let [timeout (timeouts game-id)]
    (do (when-not (future-done? timeout)
          (future-cancel timeout))
        (dissoc timeouts game-id))
    timeouts))

(defn evaluate-answers!
  [^String game-id]
  (swap! timeouts (partial cancel-timeout! game-id))
  (let [question (current-question game-id)
        scores (->> game-id
                    get-answers
                    (score-answers question))]
                    ;scale-scores-by-answer-times)
    (->> [(store-scores scores)
          (add-scores scores)
          [[:db/add [:game/id game-id] :game/state :show-answers]]]
         (reduce into)
         (d/transact db-conn))))

(defn get-answer-ids
  [^String game-id]
  (d/q '[:find [?answer ...]
         :in $ ?game-id
         :where [?game :game/id ?game-id]
                [?game :game/answers ?answer]]
       @db-conn
       game-id))

(defn next-question!
  "Get the next question for `game-id`.
  Removes the question from the game and sets it as the current question.
  Returns nil if there are no more questions."
  [^String game-id]
  (when-let [question (d/q '[:find ?question .
                             :in $ ?game-id
                             :where [?game :game/id ?game-id]
                                    [?game :game/questions ?question]]
                           @db-conn
                           game-id)]
    (->> game-id
         get-answer-ids
         (mapv (partial vector :db/retractEntity))
         (into [[:db/add [:game/id game-id] :game/state :question]
                [:db/add [:game/id game-id] :game/current-question question]
                [:db/retract [:game/id game-id] :game/questions question]])
         (d/transact db-conn))
    (swap! timeouts
           assoc
           game-id
           (future
             (Thread/sleep ^int (* 1000 (:question-time-out config)))
             (log/infof "Time-out in game %s!" game-id)
             (evaluate-answers! game-id)))))

(defn answer-question!
  "Answer the current question in game with `game-id`
  by `answer` for the player with `player-id`."
  [^String game-id
   ^String player-id
   ^String answer]
  (cond (not (@timeouts game-id))
        {:error :errors/time-out}

        (some? answer)
        (do
          (log/infof "Player %s in game %s answers '%s'." player-id game-id answer)
          (d/transact db-conn [{:game/id game-id
                                :game/answers [{:answer/player [:player/id player-id]
                                                :answer/answer (str answer)}]}])
          ; FIXME: This might be dropped due to throttling.
          ;(a/>!! refresh-channel {:game-id game-id :signals {:answer nil}}) ; Reset the $answer signal.
          (when (all-players-answered? game-id)
            (log/infof "All players in game %s have answered." game-id)
            (evaluate-answers! game-id)))))

(defn player-score
  [^String player-id])

(defn disconnect-player!
  [^String player-id]
  (log/infof "Disconnecting player %s." player-id)
  (d/transact db-conn [[:db/retractEntity [:player/id player-id]]]))

(defn game-progress
  [^String game-id]
  (-> '[:find (pull ?game [:game/questions :game/questions-total]) .
        :in $ ?game-id
        :where [?game :game/id ?game-id]]
      (d/q @db-conn game-id)
      (update :game/questions count)))

(game-progress "Co81uOWd9BtYxTG7-eu8PvEkTh8")

(defn answer-progress
  [^String game-id]
  (->> game-id
       (d/q '[:find (count-distinct ?player) (sum ?answer)
              :in $ ?game-id
              :keys total answered
              :where [?game :game/id ?game-id]
                     [?game :game/players ?player]
                     (or-join [?game ?player ?answer]
                              (and [?game :game/answers ?answer-entity]
                                   [?answer-entity :answer/player ?player]
                                   [(ground 1) ?answer])
                              [(ground 0) ?answer])]
            @db-conn)
       first))

(defn player-answer
  [^String game-id
   ^String player-id]
  (d/q '[:find (pull ?answer [:answer/score :answer/correct?]) .
         :in $ ?game-id ?player-id
         :where [?game :game/id ?game-id]
                [?game :game/players ?player]
                [?player :player/id ?player-id]
                [?game :game/answers ?answer]
                [?answer :answer/player ?player]]
       @db-conn
       game-id
       player-id))
