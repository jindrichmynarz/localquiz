(ns net.mynarz.localquiz.views.player
  (:require [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :refer [long-str]]
            [net.mynarz.localquiz.views.common :as views]))

(def waiting-icon
  [:p.waiting-icon
   [:i.material-icons
    {:data-signals:_iconIndex "0"
     :data-on-interval__duration.3s "$_iconIndex++"
     :data-text "['hourglass_empty', 'hourglass_bottom', 'hourglass_top'][$_iconIndex % 3]"}]
   [:span.shadow]])

(defn player-name-input
  ([request]
   (player-name-input request nil))
  ([{{:keys [game-id]} :path-params}
    validation-error]
   (let [disabled? (some? validation-error)
         validate-js (long-str "!$_submitted &&"
                               "$playerName.length != 0 &&"
                               (format "@post('/join/%s/validate', {requestCancellation: $_controller})" game-id))]
     [:section#content
      [:p
       {:data-signals "{_controller: new AbortController(), _submitted: false}"}
       [:label
        {:for "player-name"}
        "Player name"]
       [:input
        {:aria-live "polite"
         :autofocus true
         :data-bind "playerName"
         :aria-errormessage "name-error"
         :aria-invalid disabled?
         :data-on:keydown views/submit-by-enter
         :data-on:keydown__debounce.500ms validate-js
         :id "player-name"
         :minlength 1
         :maxlength 20
         :required true
         :type "text"}]
       [:button.btn#submit
        {:aria-disabled disabled?
         :disabled disabled?
         :data-on:click (format "$_controller.abort(); $_submitted = true; @post('/join/%s')" game-id)}
        "Join game"]]
      (when validation-error
        [:p#name-error.error validation-error])])))

(defmethod views/game-view [:player nil]
  [_]
  [:section#content
   [:h2.error "This game does not exist!"]])

(defmethod views/game-view [:player :new]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :as request}]
  (if (game/player-in-game? game-id player-id)
    [:section#content
     waiting-icon
     [:p "Please wait for the game to start."]]
    (player-name-input request)))

(defmethod views/game-view [:player :question]
  [{{:keys [game-id]} :path-params
    player-id :sid}]
  [:section#content
   (if (game/player-answered? player-id)
     [:div
      waiting-icon
      [:p "Waiting for other answers&ldots;"]]
     (let [answer-revealed? (game/all-players-answered? game-id)
           current-question (game/current-question game-id)]
       (views/answers-view false
                           answer-revealed?
                           game-id
                           current-question)))])

(defmethod views/game-view [:player :show-answers]
  [{player-id :sid}]
  ; TODO: Show if the player's answer was correct or not?
  ;       This requires either re-evaluating whether the answer is correct or persisting the evaluation.
  [:section#content "SHOW ANSWERS"])

(defmethod views/game-view [:player :leaderboard]
  [{{:keys [game-id]} :path-params
    player-id :sid}]
  ; TODO: What to show for the intermediate leaderboard?
  (when (and (game/all-questions-answered? game-id) (= player-id (game/winner game-id)))
    [:section#content
     [:h1.winner "You won!" [:i.material-icons.md-36 "emoji_events"]]]))
