(ns net.mynarz.localquiz.views.player
  (:require [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.views.common :refer [game-view]]))

(defn player-name-input
  ([request]
   (player-name-input request nil))
  ([{{:keys [game-id]} :path-params}
    validation-error]
   (let [disabled? (some? validation-error)]
     [:div#player-name-input
      [:label
       {:for "player-name"}
       "Player name"]
      [:input
       {:aria-live "polite"
        :autofocus true
        :data-bind "player-name"
        :aria-errormessage "name-error"
        :aria-invalid disabled?
        :data-on:keydown "evt.key === 'Enter' && document.getElementById('submit').click()"
        :data-on:keydown__debounce.500ms (format "@post('/join/%s/validate')" game-id)
        :id "player-name"
        :minlength 1
        :maxlength 20
        :required true
        :type "text"}]
      [:button#submit
       {:aria-disabled disabled?
        :disabled disabled?
        :data-on:click (format "@post('/join/%s')" game-id)}
       "Join game"]
      (when validation-error
        [:p#name-error.error validation-error])])))

(defmethod game-view [:player nil]
  [_]
  [:h2.error "This game does not exist!"])

(defmethod game-view [:player :new]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :as request}]
  (if (game/player-in-game? game-id player-id)
    [:h2 "Please wait for the game to start."]
    (player-name-input request)))

(defmethod game-view [:player :started]
  [_]
  [:h2 "The game has started!"])
