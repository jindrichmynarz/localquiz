(ns net.mynarz.localquiz.views.player
  (:require [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :refer [decimal-format long-str svg]]
            [net.mynarz.localquiz.views.common :as views]))

(def waiting-icon
  [:div.waiting-icon
   [:i.material-icons (svg "hourglass_empty.svg")]
   [:div.shadow]])

(defn exit-game
  [tr
   ^String game-id]
  [:button.btn
   {:data-on:click (format "@post('/leave/%s')" game-id)}
   [:i.material-icons (svg "cancel.svg")]
   (tr [:exit-game])])

(defn player-name-input
  [{{:keys [game-id]} :path-params
    :tempura/keys [tr]}]
  (let [validate-js (long-str "!$_submitted &&"
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
        :data-on:keydown__debounce.500ms validate-js
        :data-attr:aria-invalid "!!$error"
        :minlength 1
        :maxlength 20
        :name "player-name"
        :required true
        :type "text"}]
      views/lang-input
      [:button.btn#submit
       {:data-attr:aria-disabled "!!$error"
        :data-attr:disabled "!!$error"
        :data-on:click (long-str "$_controller.abort();"
                                 "$_submitted = true;"
                                 (views/post (str "/join/" game-id)))}
       [:i.material-icons (svg "play_circle.svg")]
       (tr [:join-game])]]]))

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
  (if (game/player-in-game? game-id player-id)
    (views/morph-body
      request
      (exit-game tr game-id)
      [:section#content
        waiting-icon
        [:h2 (tr [:wait-for-game-start])]])
    (views/morph-body
      request
      (player-name-input request))))

(defmethod views/game-view [:player :question]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    (exit-game tr game-id)
    [:section#content
     [:h2.error
      {:data-show "$error"
       :data-text "$error"}]
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

(defmethod views/game-view [:player :voting]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    (exit-game tr game-id)
    [:section#content
     [:h2.error
      {:data-show "$error"
       :data-text "$error"}]
     (if (game/player-voted? player-id)
       [waiting-icon
        [:h2 (tr [:wait-for-votes])]]
       [:form#answers
        [:p [:small (tr [:vote-instruction])]]
        [:ul#choices
         {:data-on:click (str "evt.target.tagName == 'INPUT' &&"
                              (views/post (str "/vote/" game-id)))}
         (for [text (game/answers-for-voting game-id player-id)]
           [:label.btn
            [:input
             {:name "answer"
              :type "checkbox"
              :value text}]
            [:div.answer [:div text]]])]])]))

(defmethod views/game-view [:player :show-answers]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    (exit-game tr game-id)
    (let [{:answer/keys [consensus correct? majority votes]
           :as answer} (game/player-answer game-id player-id)]
      [:section#content
       [:h2
        (cond (some? correct?) [:i.material-icons.answer-mark
                                (if correct?
                                  (svg "check.svg")
                                  (svg "close.svg"))]
              (some? consensus) (tr [:consensus-evaluation] [(decimal-format consensus)])
              (some? majority) (tr [(if majority :majority-gained :majority-failed)])
              (some? votes) (tr [:voting-evalution] [(decimal-format votes)])
              (nil? answer) (tr [:no-answer]))]])))

(defmethod views/game-view [:player :leaderboard]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    (exit-game tr game-id)
    [:section#content
     (if (game/all-questions-answered? game-id)
       (if ((game/winners game-id) player-id)
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
