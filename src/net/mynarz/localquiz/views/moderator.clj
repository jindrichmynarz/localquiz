(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [net.mynarz.localquiz.util :refer [decimal-format]]
            [net.mynarz.localquiz.views.common :refer [answers-view game-view]]
            [charred.api :as charred]
            [taoensso.timbre :as log]))

(defn pick-questions
  [{:keys [tr]
    game-id :sid}]
  [:div
   [:h1 "Localquiz"]
   [:label
    {:for "questions-upload"}
    (tr [:upload-questions])]
   [:input
    {:accept ".edn"
     :id "questions-upload"
     :type "file"}]
   [:button.btn
    {:data:on-click "@post('/')"}
    (tr [:submit])]])

(defn copy-button
  [tr
   ^String join-game-url]
  (let [copy-labels (charred/write-json-str [(tr [:copy]) (tr [:copied])])]
    [:span.copy-button-wrapper
     {:data-signals:_copy-label copy-labels}
     [:button.btn#copy-join-url
      {:data-on:mousedown (format "navigator.clipboard.writeText('%s');
                                  $_copyLabel.reverse();
                                  setTimeout(() => $_copyLabel.reverse(), 2000);"
                                  join-game-url)
       :data-text "$_copyLabel[0]"}]]))

(defn next-button
  [tr
   ^String next-action]
  [:button.btn.btn-primary
   {:data-on:click next-action
    :data-on:keydown__window (str "evt.key === 'Enter' && " next-action)}
   (tr [:next])
   [:i.material-icons "arrow_circle_right"]])

(def end-game
  [:span#end-game
   {:data-on:click "confirm('Do you want to end the game?') && @post('/end')"
    :title "End the game"}
   [:i.material-icons.md-light.md-36 "cancel"]])

(defn leaderboard
  [tr
   ^String game-id]
  (let [{:game/keys [questions questions-total]} (game/game-progress game-id)
        questions-answered (- questions-total questions)
        leaderboard-data (game/leaderboard game-id)
        max-score (->> leaderboard-data
                       (map :score)
                       (apply max))]
    [:div#leaderboard
     [:table
      [:caption
       [:progress
        {:max questions-total
         :value questions-answered}]]
      [:thead
       [:tr
        [:th (tr [:player])]
        [:th (tr [:score])]
        [:th]]]
      [:tbody
       (for [{:keys [player-name score]} leaderboard-data
             :let [score-decimal (decimal-format score)
                   score-style (->> (if (zero? score) score (/ score max-score))
                                    decimal-format
                                    (format "--score: %s;"))]]
         [:tr
          [:td player-name]
          [:td [:span.score-bar {:style score-style}]]
          [:td score-decimal]])]]]))

(defmethod game-view [:moderator nil]
  [{:keys [tr]}]
  [:section#create-game
   [:button.btn.btn-primary
    {:data-on:mousedown "@post('/create')"}
    (tr [:create-game])]])

(defmethod game-view [:moderator :new]
  [{:keys [tr]
    game-id :sid}]
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
        (copy-button tr play-game-url)]
       (if has-enough-players?
         [:p
          [:button.btn.btn-primary
           {:data-on:click "@post('/question')"
            :disabled (not has-enough-players?)
            :type "submit"}
           (tr [:start-game])]]
         [:p#waiting-for-players
          [:img {:src "img/wifi_exercise_animated.svg"}]
          (tr [:wait-for-players])])]]
     (when (seq lobby)
       [:section#lobby
        [:table
         [:thead [:tr [:th (tr [:players])]]]
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
  [tr
   ^String game-id
   ^Boolean answer-revealed?]
  (let [{:keys [scoring] :as question} (game/current-question game-id)
        {:keys [total answered]} (game/answer-progress game-id)
        answer-progress-text (format "%d/%d" answered total)
        scoring-icon (if (= scoring :consensus)
                       "join_inner"
                       "task_alt")]
     [:section#content
      end-game
      (timer answer-revealed?)
      [:div#question
       [:p#answer-progress
        [:label
         (tr [:players-answered])
         [:br]
         [:progress
          {:max total
           :value answered}
          answer-progress-text]]]
       (:text question)
       [:i.material-icons.md-36.scoring-icon scoring-icon]
       (answers-view tr
                     true
                     answer-revealed?
                     game-id
                     question)]
      (when answer-revealed?
        [:p (next-button tr "@post('/leaderboard')")])]))

(defmethod game-view [:moderator :question]
  [{:keys [tr]
    game-id :sid}]
  (question-view tr game-id false))

(defmethod game-view [:moderator :show-answers]
  [{:keys [tr]
    game-id :sid}]
  (question-view tr game-id true))

(defmethod game-view [:moderator :leaderboard]
  [{:keys [tr]
    game-id :sid}]
  (if (game/all-questions-answered? game-id)
    [:section#content
     (leaderboard tr game-id)
     [:p
      [:button.btn.btn-primary
       {:data-on:click "@post('/end')"}
       (tr [:end-game])
       [:i.material-icons.md-light.md-36 "cancel"]]]]
    [:section#content
     end-game
     (leaderboard tr game-id)
     [:p (next-button tr "@post('/question')")]]))
