(ns net.mynarz.localquiz.game
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.question-spec :as qs]
            [net.mynarz.localquiz.scoring :as scoring]
            [net.mynarz.localquiz.spec :as spec]
            [net.mynarz.localquiz.util :as util]
            [clojure.spec.alpha :as s]
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

(defn player-name-valid?
  [^String player-name]
  (s/valid? ::spec/player-name player-name))

(defn validate-player-name
  [^String game-id
   ^String player-name]
  (log/infof "Validating player name '%s'." player-name)
  (cond
    (not (player-name-valid? player-name)) :errors.player-name/length
    (player-name-in-game? game-id player-name) :errors.player-name/taken))

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

(defn get-defs
  "Return the defs for `game-id` as a map of id → parsed value."
  [^String game-id]
  (->> (d/q '[:find ?id ?value
              :in $ ?game-id
              :where [?game :game/id ?game-id]
                     [?game :game/defs ?def]
                     [?def :def/id ?id]
                     [?def :def/value ?value]]
            @db-conn game-id)
       (into {} (map (fn [[id value]] [id (edn/read-string value)])))))

(defn current-question
  "Get the current question for `game-id`."
  [^String game-id]
  (some->> game-id
           (d/q '[:find ?current-question .
                  :in $ ?game-id
                  :where [?game :game/id ?game-id]
                         [?game :game/current-question ?current-question]]
                @db-conn)
           edn/read-string
           (qs/resolve-refs (get-defs game-id))))

(defn parse-answer
  "Parse `answer` to Clojure data types."
  [^String answer]
  (st/coerce ::spec/player-answer answer st/string-transformer))

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

(defn leaderboard
  "Get the player leaderboard for `game-id` using the database `conn`."
  [^String game-id]
  (some->> game-id
           (d/q '[:find [(pull ?player [[:player/id :as :player-id]
                                        [:player/name :as :player-name]
                                        {:answer/_player [[:answer/score :as :score]]}
                                        [:player/score :default 0.0 :as :total-score]]) ...]
                  :in $ ?game-id
                  :where [?game :game/id ?game-id]
                         [?game :game/players ?player]]
                @db-conn)
           (sort-by :total-score util/descending-order)
           (partition-by :total-score)
           (map-indexed (fn [index players]
                          (map (fn [{[{:keys [score]} & _] :answer/_player
                                     :as player}]
                                 (assoc player
                                        :index (inc index)
                                        :score (or score 0.0)
                                        :winner? (zero? index)))
                               players)))
           (apply concat)))

(defn leaderboard!
  "Transition the game with `game-id` to the leaderboard state."
  [^String game-id]
  (d/transact db-conn [[:db/add [:game/id game-id] :game/state :leaderboard]]))

(defn winners
  "Get a set of ID(s) of the winning player(s)."
  [^String game-id]
  (->> game-id
       leaderboard
       (filter :winner?)
       (map :player-id)
       set))

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
  (let [{:keys [scoring]
         :as question} (current-question game-id)
        scores (cond-> (->> game-id
                            get-answers
                            (scoring/score-answers question))
                  (not= scoring :consensus) scoring/scale-scores-by-answer-times)]
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

(defn schedule-timeout
  "Schedule a timeout for the game identified by `game-id`."
  [^String game-id]
  (swap! timeouts
         assoc
         game-id
         (future
           (Thread/sleep ^int (* 1000 (:question-time-out config)))
           (log/infof "Time-out in game %s!" game-id)
           (evaluate-answers! game-id))))

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
    (schedule-timeout game-id)))

(defn answer-question!
  "Answer the current question in game with `game-id`
  by `answer` for the player with `player-id`."
  [^String game-id
   ^String player-id
   ^String answer]
  (cond (not (@timeouts game-id))
        {:error :errors/time-out}

        (and (some? answer) (not (player-answered? player-id)))
        (do
          (log/infof "Player %s in game %s answers '%s'." player-id game-id answer)
          (d/transact db-conn [{:game/id game-id
                                :game/answers [{:answer/player [:player/id player-id]
                                                :answer/answer (str answer)}]}])
          (when (all-players-answered? game-id)
            (log/infof "All players in game %s have answered." game-id)
            (evaluate-answers! game-id)))))

(defn create-game!
  "Create a game with `game-id` from the given `questions` and optional `defs`."
  ([^String game-id
    questions]
   (create-game! game-id questions []))
  ([^String game-id
    questions
    defs]
   (log/infof "Creating a new game %s." game-id)
   (d/transact db-conn [(cond-> {:game/id game-id
                                 :game/state :new
                                 :game/questions questions
                                 :game/questions-total (-> questions count long)}
                          (seq defs) (assoc :game/defs defs))])))

(defn end-game-sessions!
  "End sessions associated with the game with `game-id`."
  [^String game-id]
  (let [sessions (d/q '[:find [?session ...]
                        :in $ ?game-id
                        :where (or-join [?game-id ?session]
                                  [?session :session/id ?game-id]
                                  (and [?game :game/id ?game-id]
                                       [?game :game/players ?player]
                                       [?player :player/id ?player-id]
                                       [?session :session/id ?player-id]))]
                      @db-conn
                      game-id)]
    (->> sessions
         (mapv (fn [session] [:db/retractEntity {:db/id session}]))
         (d/transact db-conn))))

(defn end-game!
  "End the game with `game-id`."
  [^String game-id]
  (log/infof "Ending the game %s." game-id)
  (end-game-sessions! game-id)
  (d/transact db-conn [[:db/retractEntity [:game/id game-id]]]))

(def transitions
  {:new          next-question!
   :show-answers leaderboard!
   :leaderboard  next-question!})

(defn advance!
  "Advance the game with `game-id` to its next state."
  [^String game-id]
  (some-> game-id get-game-state transitions (apply [game-id])))

(defn join-game
  "Transaction function that adds `player-id` with `player-name` to the game with `game-id`.
  Requires the game to be in :new state."
  [db
   ^String game-id
   ^String player-id
   ^String player-name]
  (when-not (= :new (:game/state (d/entity db [:game/id game-id])))
    (throw (ex-info "Cannot join a game that is not in :new state."
                    {:error :game-already-started})))
  [{:db/id [:game/id game-id]
    :game/players [{:player/id player-id
                    :player/name player-name}]}])

(defn join-game!
  "Add a player with `player-id` and `player-name` to the game with `game-id`."
  [^String game-id
   ^String player-id
   ^String player-name]
  (d/transact db-conn [[:db.fn/call join-game game-id player-id player-name]]))

(defn disconnect-player!
  [^String player-id]
  (log/infof "Disconnecting player %s." player-id)
  (d/transact db-conn [[:db/retractEntity [:player/id player-id]]]))

(defn merge-session-params
  "Transaction function that merges `params` into :session/params for `session-id`."
  [db
   ^String session-id
   params]
  (let [current-params (some-> (d/entity db [:session/id session-id])
                               :session/params
                               edn/read-string)
        merged-params (util/deep-merge current-params params)]
    [{:session/id session-id
      :session/params (pr-str merged-params)}]))

(defn merge-session-params!
  "Merge `params` into :session/params for `session-id`."
  [^String session-id
   params]
  (d/transact db-conn [[:db.fn/call merge-session-params session-id params]]))

(defn get-session-params
  "Return the session params map for `session-id`, or nil if absent."
  [^String session-id]
  (some-> @db-conn
          (d/entity [:session/id session-id])
          :session/params
          edn/read-string))

(defn get-session-def
  "Return the parsed def value for `def-id` in the game whose :game/id equals `session-id`."
  [^String session-id
   def-id]
  (some-> (d/q '[:find ?value .
                 :in $ ?session-id ?def-id
                 :where [?game :game/id ?session-id]
                        [?game :game/defs ?def]
                        [?def :def/id ?def-id]
                        [?def :def/value ?value]]
               @db-conn
               session-id
               def-id)
          edn/read-string))

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
         {:answer/keys [score]} :answer
         :keys [answer
                player-count
                same-answer-count]} (-> query
                                        (d/q @db-conn game-id player-id)
                                        first
                                        (update :current-question edn/read-string))]
    (cond-> answer
      (= scoring :consensus)
      (assoc :answer/consensus (* (/ (dec same-answer-count) (dec player-count)) 100))

      (= scoring :majority)
      (assoc :answer/majority (pos? score)))))

(defn game-players
  "Get the names of players in game with `game-id`."
  [^String game-id]
  (d/q '[:find ?player-name
         :keys player-name
         :in $ ?game-id
         :where [?game :game/id ?game-id]
                [?game :game/players ?player]
                [?player :player/name ?player-name]]
       @db-conn
       game-id))
