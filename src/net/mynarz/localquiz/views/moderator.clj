(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [net.mynarz.localquiz.util :refer [decimal-format]]
            [net.mynarz.localquiz.views.common :refer [answers-view game-view]]
            [taoensso.timbre :as log]))

(defn pick-questions
  [{game-id :sid}]
  [:div
   [:h1 "Localquiz"]
   [:label
    {:for "questions-upload"}
    "Upload questions"]
   [:input
    {:accept ".edn"
     :id "questions-upload"
     :type "file"}]
   [:button.btn {:data:on-click "@post('/')"} "Upload"]])

(defn copy-button
  [^String join-game-url]
  [:span.copy-button-wrapper
   {:data-signals:_copy-label "['Copy', 'Copied!']"}
   [:button.btn#copy-join-url
    {:data-on:mousedown (format "navigator.clipboard.writeText('%s');
                                $_copyLabel.reverse();
                                setTimeout(() => $_copyLabel.reverse(), 2000);"
                                join-game-url)
     :data-text "$_copyLabel[0]"}]])

(defn next-button
  [^String next-action]
  [:button.btn.btn-primary
   {:data-on:click next-action
    :data-on:keydown__window (str "evt.key === 'Enter' && " next-action)}
   "Next"
   [:i.material-icons "arrow_circle_right"]])

(def end-game
  [:span#end-game
   {:data-on:click "confirm('Do you want to end the game?') && @post('/end')"
    :title "End the game"}
   [:i.material-icons.md-light.md-36 "cancel"]])

(defn leaderboard
  [^String game-id]
  (let [{:keys [questions-remaining questions-total]} (game/game-progress game-id)
        questions-answered (- questions-total questions-remaining)]
    [:div#leaderboard
     [:table
      [:caption
       [:progress
        {:max questions-total
         :value questions-answered}]]
      [:thead [:tr [:th "Player"] [:th "Score"]]]
      [:tbody
       (for [{:keys [player-name score]} (game/leaderboard game-id)
             :let [score-decimal (decimal-format score)
                   score-style (format "--score: %s;" score-decimal)]]
         [:tr
          [:td player-name]
          [:td [:span {:style score-style}] score-decimal]])]]]))

(defmethod game-view [:moderator nil]
  [_]
  [:section#create-game
   [:button.btn.btn-primary
    {:data-on:mousedown "@post('/create')"} "Create a game"]])

(defmethod game-view [:moderator :new]
  [{game-id :sid}]
  (let [play-game-url (str (:url config) "/play/" game-id)
        lobby (game/lobby game-id)
        has-enough-players? (game/has-enough-players? game-id)]
    [:div#sections
     end-game
     [:section#join-game
      [:div
       [:div#qrcode (url->qrcode-svg play-game-url)]
       [:p
        [:input#game-url
         {:readonly true
          :type "text"
          :value play-game-url}]
        (copy-button play-game-url)]
       (if has-enough-players?
         [:p
          [:button.btn.btn-primary
           {:data-on:click "@post('/question')"
            :disabled (not has-enough-players?)
            :type "submit"}
           "Start the game"]]
         [:p#waiting-for-players
          [:img {:src "img/wifi_exercise_animated.svg"}]
          "Waiting for at least two players to join..."])]]
     (when (seq lobby)
       [:section#lobby
        [:table
         [:thead [:tr [:th "Players"]]]
         [:tbody
          (for [player-name lobby]
            [:tr [:td player-name]])]]])]))

(defn timer
  [^Boolean answer-revealed?]
  (when-not answer-revealed?
    (let [duration (:question-time-out config)]
      [:div.timer
       {:data-style:--duration (format "'%ds'" duration)}
       [:div]])))

(defn question-view
  [^String game-id
   ^Boolean answer-revealed?]
  (let [question (game/current-question game-id)]
     [:section#content
      end-game
      (timer answer-revealed?)
      [:div#question
       (:text question)
       (answers-view true
                     answer-revealed?
                     game-id
                     question)]
      (when answer-revealed?
        [:p (next-button "@post('/leaderboard')")])]))

(defmethod game-view [:moderator :question]
  [{game-id :sid}]
  (question-view game-id false))

(defmethod game-view [:moderator :show-answers]
  [{game-id :sid}]
  (question-view game-id true))

(defmethod game-view [:moderator :leaderboard]
  [{game-id :sid}]
  (if (game/all-questions-answered? game-id)
    [:section#content
     (leaderboard game-id)
     [:p
      [:button.btn.btn-primary
       {:data-on:click "@post('/end')"}
       "End game"
       [:i.material-icons.md-light.md-36 "cancel"]]]]
    [:section#content
     end-game
     (leaderboard game-id)
     [:p
      (next-button "@post('/question')")]]))
