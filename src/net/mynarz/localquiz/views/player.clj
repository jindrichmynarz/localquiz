(ns net.mynarz.localquiz.views.player
  (:require [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.views.common :as views]))

(defn player-name-input
  ([request]
   (player-name-input request nil))
  ([{{:keys [game-id]} :path-params}
    validation-error]
   (let [disabled? (some? validation-error)
         validate-js (format "$_submitted || @post('/join/%s/validate', {requestCancellation: $_controller})" game-id)]
     [:section#content
      [:p
       {:data-signals "{_controller: new AbortController(), _submitted: false}"}
       [:label
        {:for "player-name"}
        "Player name"]
       [:input
        {:aria-live "polite"
         :autofocus true
         :data-bind "player-name"
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
    [:section#content [:p "Please wait for the game to start."]]
    (player-name-input request)))

(defmethod views/game-view [:player :question]
  [{{:keys [game-id]} :path-params
    player-id :sid}]
  [:section#content
   (if (game/player-answered? player-id)
     [:p "Waiting for other answers..."]
     (let [answer-revealed? (game/all-players-answered? game-id)
           current-question (game/current-question game-id)]
       (views/answers-view false
                           answer-revealed?
                           game-id
                           current-question)))])

(defmethod views/game-view [:player :show-answers]
  [{player-id :sid}]
  [:section#content])
    ; TODO: Show if the player's answers was correct or not?

(defmethod views/game-view [:player :leaderboard]
  [])

(defmethod views/game-view [:player :end]
  [])
