(ns net.mynarz.localquiz.game
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.scoring :as scoring]
            [net.mynarz.localquiz.spec :as s]
            [clojure.string :as string]
            [datahike.api :as d]
            [fast-edn.core :as edn]
            [taoensso.timbre :as log]
            [spec-tools.core :as st]))

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
  "Parse `answer` to Clojure data types."
  [^String answer]
  (st/coerce ::s/player-answer answer st/string-transformer))

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
  "Get the current answers for `game-id`."
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

(defn add-score
  "A transaction function that adds `answer-score` to the current score of the `player`."
  [db
   ^long player
   ^double answer-score]
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
  (some->> game-id
           (d/q '[:find (pull ?game [{:game/players [[:player/id :as :player-id]
                                                     [:player/name :as :player-name]
                                                     [:player/score :default 0.0 :as :score]]}]) .
                  :in $ ?game-id
                  :where [?game :game/id ?game-id]]
                @db-conn)
           :game/players
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
  [timeouts
   ^String game-id]
  (if-let [timeout (timeouts game-id)]
    (do (or (future-done? timeout) (future-cancel timeout))
        (dissoc timeouts game-id))
    timeouts))

(defn evaluate-answers!
  [^String game-id]
  (swap! timeouts cancel-timeout! game-id)
  ; FIXME: Is it possible that this is evaluated more than once for the same question?
  ;        Shall we store a "lock" indicating if the question was already evaluated? Don't evaluate answers if they were evaluated before.
  (log/infof "Evaluating answers for game %s." game-id)
  (let [question (current-question game-id)
        scores (->> game-id
                    get-answers
                    (scoring/score-answers question)
                    scoring/scale-scores-by-answer-times)]
    (->> [(store-scores scores)
          (add-scores scores)
          [[:db/add [:game/id game-id] :game/state :show-answers]]]
         (reduce into)
         log/spy
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
             (log/infof "Time-out in game %s for question %s!" game-id question)
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
          (when (all-players-answered? game-id)
            (log/infof "All players in game %s have answered." game-id)
            (evaluate-answers! game-id)))))

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
  (let [query '[:find (pull ?answer [:answer/score :answer/correct?])
                      ?current-question
                      (count-distinct ?other-answer)
                      (count-distinct ?other-player)
                :in $ ?game-id ?player-id
                :keys answer current-question same-answer-count player-count
                :where [?game :game/id ?game-id]
                       [?game :game/players ?player]
                       [?player :player/id ?player-id]
                       [?game :game/answers ?answer]
                       [?game :game/current-question ?current-question]
                       [?answer :answer/player ?player]
                       [?answer :answer/answer ?value]
                       [?other-answer :answer/answer ?value]
                       [?game :game/players ?other-player]]
        {{:keys [scoring]} :current-question
         :keys [answer
                player-count
                same-answer-count]} (-> query
                                        (d/q @db-conn game-id player-id)
                                        first
                                        (update :current-question edn/read-string))]
    (cond-> answer
      (= scoring :consensus)
      (assoc :answer/consensus (* (/ (dec same-answer-count) (dec player-count)) 100)))))
