(ns net.mynarz.localquiz.views.player
  (:require [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :refer [decimal-format long-str svg]]
            [net.mynarz.localquiz.views.common :as views]))

(def waiting-icon
  [:div.waiting-icon
   [:i.material-icons (svg "hourglass_empty.svg")]
   [:div.shadow]])

(defn player-name-input
  [{{:keys [game-id]} :path-params
    :tempura/keys [tr]
    :keys [error]}]
  (let [disabled? (some? error)
        validate-js (long-str "!$_submitted &&"
                              (views/post (str "/join/" game-id "/validate")
                                          "requestCancellation: $_controller"))]
    [:section#content
     [:form#player-name-input
      {:data-signals "{_controller: new AbortController(),
                        _submitted: false}"}
      [:label
       {:for "player-name"}
       (tr [:player-name])]
      [:input#player-name
       {:aria-live "polite"
        :autofocus true
        :aria-errormessage "name-error"
        :aria-invalid disabled?
        :data-on:keydown__debounce.500ms validate-js
        :minlength 1
        :maxlength 20
        :name "player-name"
        :required true
        :type "text"}]
      views/lang-input
      [:button.btn#submit
       {:aria-disabled disabled?
        :disabled disabled?
        :data-on:click (long-str "$_controller.abort();"
                                 "$_submitted = true;"
                                 (views/post (str "/join/" game-id)))}
       [:i.material-icons (svg "play_circle.svg")]
       (tr [:join-game])]]
     (when error
       [:p#name-error.error
        (tr [error])])]))

(defmethod views/game-view [:player nil]
  [{:tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    [:section#content
     [:h2.error
      [:i.material-icons (svg "videogame_asset_off.svg")]
      (tr [:game-not-exists])]]))

(defmethod views/game-view [:player :new]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    (if (game/player-in-game? game-id player-id)
      [:section#content
       waiting-icon
       [:h2 (tr [:wait-for-game-start])]]
      (player-name-input request))))

(defmethod views/game-view [:player :question]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    [:section#content
     (if (game/player-answered? player-id)
       [waiting-icon
        [:h2 (tr [:wait-for-answers])]]
       (let [answer-revealed? (game/all-players-answered? game-id)
             current-question (game/current-question game-id)]
         (views/answers-view tr
                             false
                             answer-revealed?
                             game-id
                             false
                             current-question)))]))

(defmethod views/game-view [:player :show-answers]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    (let [{:answer/keys [consensus correct?] :as answer} (game/player-answer game-id player-id)]
      [:section#content
       [:h2
        (cond (some? correct?) [:i.material-icons.answer-mark
                                (if correct?
                                  (svg "check.svg")
                                  (svg "close.svg"))]
              (some? consensus) (tr [:consensus-evaluation] [(decimal-format consensus)])
              (nil? answer) (tr [:no-answer]))]])))

(defmethod views/game-view [:player :leaderboard]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    [:section#content
     (if (game/all-questions-answered? game-id)
       (if (= player-id (game/winner game-id))
         [:div.verdict.winner
          [:i.material-icons (svg "emoji_events.svg")]
          [:h2 (tr [:you-won])]]
         [:div.verdict
          [:i.material-icons (svg "sentiment_very_dissatisfied.svg")]
          [:h2 (tr [:you-lost])]])
       (if-let [{:answer/keys [score]} (game/player-answer game-id player-id)]
         [:h2 (format "+ %s %s"
                      (decimal-format score)
                      (tr [(cond (= score 1.0) :point
                                 (>= score 2.0) :points
                                 :else :point-fraction)]))]
         [:h2 (tr [:no-answer])]))]))
