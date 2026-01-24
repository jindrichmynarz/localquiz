(ns net.mynarz.localquiz.views.player
  (:require [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :refer [decimal-format long-str]]
            [net.mynarz.localquiz.views.common :as views]
            [taoensso.timbre :as log]))

(def waiting-icon
  [:p.waiting-icon
   [:i.material-icons
    {:aria-hidden "true"
     :data-signals:_iconIndex "0"
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
                                  (views/post (str "/join/" game-id)))}
        [:i.material-icons
         {:aria-hidden "true"}
         "play_circle"]
        (tr [:join-game])]]
      (when validation-error
        [:p#name-error.error
         (tr [validation-error])])])))

(defmethod views/game-view [:player nil]
  [{:tempura/keys [tr]}]
  [:section#content
   [:h2.error
    [:i.material-icons.md-light
     {:aria-hidden "true"}
     "videogame_asset_off"]
    (tr [:game-not-exists])]])

(defmethod views/game-view [:player :new]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (if (game/player-in-game? game-id player-id)
    [:section#content
     waiting-icon
     [:h2 (tr [:wait-for-game-start])]]
    (player-name-input request)))

(defmethod views/game-view [:player :question]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]}]
  [:section#content
   (if (game/player-answered? player-id)
     [:div
      waiting-icon
      [:h2 (tr [:wait-for-answers])]]
     (let [answer-revealed? (game/all-players-answered? game-id)
           current-question (game/current-question game-id)]
       (views/answers-view tr
                           false
                           answer-revealed?
                           game-id
                           current-question)))])

(defmethod views/game-view [:player :show-answers]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]}]
  (let [{:answer/keys [consensus correct?] :as answer} (game/player-answer game-id player-id)]
    [:section#content
     [:h2
      (cond (some? correct?) (let [{:keys [icon label]} (if correct?
                                                          {:icon "check"
                                                           :label :correct}
                                                          {:icon "close"
                                                           :label :incorrect})]
                               [:i.material-icons.answer-mark
                                {:aria-hidden "true"
                                 :aria-label (tr [label])}
                                icon])
            (some? consensus) (tr [:consensus-evaluation] [(decimal-format consensus)])
            (nil? answer) (tr [:no-answer]))]]))

(defmethod views/game-view [:player :leaderboard]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]}]
  [:section#content
   (if (game/all-questions-answered? game-id)
     (if (= player-id (game/winner game-id))
       [:div.verdict.winner
        [:i.material-icons.md-36
         {:aria-hidden "true"}
         "emoji_events"]
        [:h2 (tr [:you-won])]]
       [:div.verdict
        [:i.material-icons.md-36
         {:aria-hidden "true"}
         "sentiment_very_dissatisfied"]
        [:h2 (tr [:you-lost])]])
     (if-let [{:answer/keys [score]} (game/player-answer game-id player-id)]
       [:h2 (format "+ %s %s"
                    (decimal-format score)
                    (tr [(cond (= score 1.0) :point
                               (>= score 2.0) :points
                               :else :point-fraction)]))]
       [:h2 (tr [:no-answer])]))])
