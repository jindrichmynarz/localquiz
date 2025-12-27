(ns net.mynarz.localquiz.game
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.normalize :refer [normalize-answer]]
            [clj-fuzzy.jaro-winkler :refer [jaro-winkler]]
            [clojure.string :as string]
            [datahike.api :as d]
            [fast-edn.core :as edn]
            [taoensso.timbre :as log]))

(defn get-session
  "Get game state and session role for the given `session-id`."
  [^String session-id]
  ; TODO: Account for the player's request prior to joining a game. -> This is already done by dispatching on :path-params.
  ;       Everyone who's not the moderator is treated as a player? 
  (->> session-id
       (d/q '[:find ?state ?session-role ?priority
              :in $ ?session-id
              :keys state session-role priority
              :where [?game :game/state ?state]
                     (or-join [?game ?session-role ?priority]
                              (and [?game :game/id ?session-id]
                                   [(ground :moderator) ?session-role]
                                   [(ground 0) ?priority])
                              (and [?player :player/id ?session-id]
                                   [?game :game/players ?player]
                                   [(ground :player) ?session-role]
                                   [(ground 1) ?priority]))]
             @db-conn)
       (sort-by :priority)
       first))

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
    (player-name-in-game? game-id player-name)
    (format "A player named '%s' is already in this game." player-name)

    (not (player-name-valid-length? player-name))
    (format "Player name must have between 1 to 20 characters.")))

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
    (d/transact db-conn [[:db/add [:game/id game-id] :game/current-question question]
                         [:db/retract [:game/id game-id] :game/questions question]])))
    ; TODO:
    ; Use a go block instead with alts!! between a time-out and game/all-players-answered?
    ; How to turn game/all-players-answered into a channel? We can do polling, but that will be inefficient.
    ;; (future
    ;;   (Thread/sleep ^int (* 1000 (:question-time-out config))))))

(defn current-question
  "Get the current question for `game-id`."
  [^String game-id]
  (when-let [question (d/q '[:find ?current-question .
                             :in $ ?game-id
                             :where [?game :game/id ?game-id]
                                    [?game :game/current-question ?current-question]]
                            @db-conn
                            game-id)]
    (edn/read-string question)))

(defn parse-answer
  [^String answer]
  (case answer
    nil nil
    "true" true
    "false" false
    (cond
      (re-matches #"^\d+$" answer) (Integer/parseInt answer)
      (re-matches #"^\d+\.\d+$" answer) (Double/parseDouble answer)
      :else answer)))

(defn answer-question!
  "Answer the current question in game with `game-id`
  by `answer` for the player with `player-id`."
  [^String game-id
   ^String player-id
   ^String answer]
  (log/infof "Player %s in game %s answers '%s'." player-id game-id answer)
  (d/transact db-conn [{:game/id game-id
                        :game/answers [{:answer/player [:player/id player-id]
                                        :answer/answer answer}]}]))

(defn player-answered?
  "Test if a player with `player-id` has already answered
  the current question in game with `game-id`."
  [^String game-id
   ^String player-id]
  (d/q '[:find ?player-id .
         :in $ ?game-id ?player-id
         :where [?game :game/id ?game-id]
                [?game :game/answers ?answer]
                [?answer :answer/player ?player]
                [?player :player/id ?player-id]]
       @db-conn
       game-id
       player-id))

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

(defn get-answers
  [^String game-id]
  (->> game-id
       (d/q '[:find ?player ?answer ?answer-time
              :in $ ?game-id
              :keys player answer answer-time
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
  {true 1
   false 0})

(defmulti score-answers (comp (juxt :type :scoring) first))

(defmethod score-answers [:multiple nil]
  [{:keys [choices]} answers]
  (for [answer answers]
    (assoc answer :score (->> answer
                              :answer
                              (nth choices)
                              (keep :correct?)
                              count))))

(defmethod score-answers [:yesno nil]
  [{:keys [correct?]} answers]
  (for [answer answers]
    (assoc answer :score (-> answer
                             :answer
                             (= correct?)
                             boolean->score))))

(defmethod score-answers [:open nil]
  [question answers]
  (let [expected (-> question :answer normalize-answer)]
    (for [answer answers
          :let [actual (-> answer :answer normalize-answer)]]
      (assoc answer :score (boolean->score (> (jaro-winkler actual expected)
                                              (:similarity-threshold config)))))))

(defmethod score-answers [:percent-range nil]
  [{:keys [percentage]} answers]
  (for [answer answers]
    (assoc answer :score (- 1 (/ (Math/abs (- (:answer answer) percentage)) 100)))))

(defmethod score-answers [:multiple :consensus]
  [question answers])

(defn scale-scores-by-answer-times
  [scores]
  (let [max-answer-time (apply max (map :answer-time scores))]
    (for [score scores
          :let [time-modifier (- 1 (/ (:answer-time score) max-answer-time))]]
      (update score :score * time-modifier))))

(defn add-score
  "Add `answer-score` to the current score of the `player`."
  [db
   ^long player
   ^long answer-score]
  (d/q '[:find ?player ?score
         :in $ ?player ?answer-score
         :keys db/id player/score
         :where [(get-else $ ?player :player/score 0) ?current-score]
                [(+ ?current-score ?answer-score) ?score]]
       db
       player
       answer-score))

(defn add-scores!
  [scores]
  (->> scores
      (mapv (fn [{:keys [player score]}] [:db.fn/call add-score player score]))
      (d/transact db-conn)))

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
                     (or-join [?player ?score]
                              [?player :player/score ?score]
                              [(ground 0) ?score])]
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

(defn disconnect-player!
  [^String player-id]
  (log/infof "Disconnecting player %s." player-id)
  (d/transact db-conn [[:db/retractEntity [:player/id player-id]]]))

(defn end-game!
  "End the game identified by `game-id`."
  [^String game-id]
  (log/infof "Ending game %s." game-id)
  (d/transact db-conn [[:db/retractEntity [:game/id game-id]]]))
