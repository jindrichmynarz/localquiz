(ns net.mynarz.localquiz.views.player
  (:require [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :refer [decimal-format long-str]]
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
  ([{{:keys [game-id]} :path-params
     :tempura/keys [tr]}
    validation-error]
   (let [disabled? (some? validation-error)
         validate-js (long-str "!$_submitted &&"
                               (views/post (format "/join/%s/validate" game-id)
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
         :data-on:keydown views/submit-by-enter
         :data-on:keydown__debounce.500ms validate-js
         :minlength 1
         :maxlength 20
         :name "player-name"
         :required true
         :type "text"}]
       [:button.btn#submit
        {:aria-disabled disabled?
         :disabled disabled?
         :data-on:click (long-str "$_controller.abort();"
                                  "$_submitted = true;"
                                  (views/post (str "/join" game-id)))}
        [:i.material-icons "play_circle"]
        (tr [:join-game])]]
      (when validation-error
        [:p#name-error.error
         (tr [validation-error])])])))

(defmethod views/game-view [:player nil]
  [{:tempura/keys [tr]}]
  [:section#content
   [:h2.error
    [:i.material-icons.md-light "videogame_asset_off"]
    (tr [:game-not-exists])]])

(defmethod views/game-view [:player :new]
  [{{:keys [game-id]} :path-params
    :tempura/keys [tr]
    player-id :sid
    :as request}]
  (if (game/player-in-game? game-id player-id)
    [:section#content
     waiting-icon
     [:p (tr [:wait-for-game-start])]]
    (player-name-input request)))

(defmethod views/game-view [:player :question]
  [{{:keys [game-id]} :path-params
    :tempura/keys [tr]
    player-id :sid}]
  [:section#content
   (if (game/player-answered? player-id)
     [:div
      waiting-icon
      [:p (tr [:wait-for-answers])]]
     (let [answer-revealed? (game/all-players-answered? game-id)
           current-question (game/current-question game-id)]
       (views/answers-view tr
                           false
                           answer-revealed?
                           game-id
                           current-question)))])

(defmethod views/game-view [:player :show-answers]
  [{:tempura/keys [tr]
    {:keys [game-id]} :path-params
    player-id :sid}]
  (let [{:answer/keys [correct?] :as answer} (game/player-answer game-id player-id)]
    [:section#content
     ; TODO: How to rate answers for the consensus questions?
     (cond (some? correct?) [:i.material-icons.answer-mark (if correct? "check" "close")]
           (nil? answer) [:h2 (tr [:no-answer])])]))

(defmethod views/game-view [:player :leaderboard]
  [{{:keys [game-id]} :path-params
    :tempura/keys [tr]
    player-id :sid}]
  [:section#content
   (if (game/all-questions-answered? game-id)
     (if (= player-id (game/winner game-id))
       [:div.verdict.winner
        [:i.material-icons.md-36 "emoji_events"]
        [:h2 (tr [:you-won])]]
       [:div.verdict
        [:i.material-icons.md-36 "sentiment_very_dissatisfied"]
        [:h2 (tr [:you-lost])]])
     (let [{:answer/keys [score]} (game/player-answer game-id player-id)
           points (format "+ %s %s"
                          (decimal-format score)
                          (tr [(cond (= score 1.0) :point
                                     (>= score 2.0) :points
                                     :else :point-fraction)]))]
       [:h2 points]))])
