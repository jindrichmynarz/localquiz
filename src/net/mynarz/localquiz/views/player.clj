(ns net.mynarz.localquiz.views.player
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :refer [decimal-format long-str svg]]
            [net.mynarz.localquiz.views.common :as views]
            [dev.onionpancakes.chassis.core :as h])
  (:import (java.util Collections LinkedHashMap Map)))

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
                       _submitted: false,
                       nameError: false}"}
      [:label
       {:for "player-name"}
       (tr [:player-name])]
      [:input#player-name
       {:autofocus true
        :aria-errormessage "name-error"
        :data-on:keydown__debounce.500ms validate-js
        :data-attr:aria-invalid "!!$nameError"
        :minlength 1
        :maxlength 20
        :name "player-name"
        :required true
        :type "text"}]
      [:p#name-error
       {:aria-live "polite"
        :data-show "$nameError"
        :data-text "$nameError"}]
      views/lang-input
      [:button.btn.btn-primary#submit
       {:data-attr:aria-disabled "!!$nameError"
        :data-attr:disabled "!!$nameError"
        :data-on:click (long-str "$_controller.abort();"
                                 "$_submitted = true;"
                                 (views/post (str "/join/" game-id)))}
       [:i.material-icons (svg "play_circle.svg")]
       (tr [:join-game])]]]))

(defn game-rules
  [tr]
  [:div#rules
   (tr [:rules] [(:question-time-out config)])])

(defmethod views/game-view [:player nil]
  [{:tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    ;; Where players land when the moderator ends the game, and where an old or mistyped
    ;; link leads, so it thanks the players without claiming the reader was one.
    [:section#content
     [:div.verdict
      [:i.material-icons (svg "waving_hand_animated.svg")]
      [:h2 (tr [:game-over])]
      [:p (tr [:thanks-for-playing])]]]))

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
        [:p (tr [:wait-for-game-start])]
        (game-rules tr)])
    (views/morph-body
      request
      (player-name-input request))))

(defonce ^:private ^Map question-sections
  ;; ponytail: LRU of 1000 entries, about one per running game and language, each a few KB.
  (Collections/synchronizedMap
    (proxy [LinkedHashMap] [16 0.75 true]
      (removeEldestEntry [_]
        (> (.size ^Map this) 1000)))))

(defn- question-section
  [tr
   ^String game-id
   ^long asked-at
   question]
  [:section#content
   ;; The answers are revealed only once the game leaves the :question state.
   (views/timer asked-at false)
   [:h2.error
    {:data-show "$error"
     :data-text "$error"}]
   (views/answers-view tr false {} game-id false question)])

(defn- shared-question-section
  "The current question, which is the same for every player in the game who speaks the
  same language, so it is rendered once per game, question and language. Except for
  :autocomplete, which reads back the fragment the player has typed."
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}
   ^long asked-at]
  (let [locales (or (:tempura/locales request)
                    (some-> request :tempura/accept-langs_ deref))
        k [game-id asked-at locales]]
    (if-some [html (.get question-sections k)]
      (h/raw html)
      (let [question (assoc (game/current-question game-id) :session-id player-id)
            section (question-section tr game-id asked-at question)]
        (if (= (:type question) :autocomplete)
          section
          (let [html (h/html section)]
            (.put question-sections k html)
            (h/raw html)))))))

(defmethod views/game-view [:player :question]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    (exit-game tr game-id)
    (let [asked-at (game/question-asked-at game-id)]
      (if (game/player-answered? player-id)
        [:section#content
         (views/timer asked-at false)
         [:h2.error
          {:data-show "$error"
           :data-text "$error"}]
         [waiting-icon
          [:h2 (tr [:wait-for-answers])]]]
        (shared-question-section request asked-at)))))

(defmethod views/game-view [:player :show-answers]
  [{{:keys [game-id]} :path-params
    player-id :sid
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    (exit-game tr game-id)
    (let [{:answer/keys [consensus correct? majority]
           :as answer} (game/player-answer game-id player-id)]
      [:section#content
       [:h2
        (cond (some? correct?) [:i.material-icons.answer-mark
                                (if correct?
                                  (svg "check.svg")
                                  (svg "close.svg"))]
              (some? consensus) (tr [:consensus-evaluation] [(decimal-format consensus)])
              (some? majority) (tr [(if majority :majority-gained :majority-failed)])
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
