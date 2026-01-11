(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [net.mynarz.localquiz.question-sources :refer [question-sources]]
            [net.mynarz.localquiz.util :refer [decimal-format]]
            [net.mynarz.localquiz.views.common :refer [answers-view game-view]]
            [charred.api :as charred]
            [taoensso.timbre :as log]))

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

(def end-game-cmd
  "@post('/end')")

(defn end-game
  [tr]
  (let [click-handler (format "confirm('%s') && %s"
                              (tr [:confirm-end-game])
                              end-game-cmd)]
    [:span#end-game
     {:data-on:click click-handler
      :title (tr [:end-game])}
     [:i.material-icons.md-light.md-36 "cancel"]]))

(defn next-button
  [tr
   ^String next-action]
  [:button.btn.btn-primary
   {:data-on:click next-action
    :data-on:keydown__window (str "evt.key === 'Enter' && " next-action)}
   (tr [:next])
   [:i.material-icons "arrow_circle_right"]])

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
  [{:tempura/keys [tr]}]
  [:section#content
   {:data-signals:_tab-shown "'select-questions'"}
   [:div.tabs
    [:ul.tab-selector
     [:li
      {:data-class:active "$_tabShown == 'select-questions'"}
      [:a
       {:data-on:click "$_tabShown = 'select-questions'"}
       (tr [:pick-questions])]]
     [:li
      {:data-class:active "$_tabShown == 'upload-questions'"}
      [:a
       {:data-on:click "$_tabShown = 'upload-questions'"}
       (tr [:upload-questions])]]]
    [:div.tab-content
     [:form
      {:data-show "$_tabShown == 'select-questions'"}
      [:select.questions-picker
       {:name "questions-source"}
       (for [question-source (keys question-sources)]
         [:option
          {:value question-source}
          question-source])]]
     [:form
      {:data-show "$_tabShown == 'upload-questions'"
       :enctype "multipart/form-data"
       :style "display: none"}
      [:input
       {:name "csrf"
        :type "hidden"
        :data-attr:value "$csrf"}]
      [:input#questions-upload
       {:accept ".edn"
        :data-on:change "@post('/create/validate', {contentType: 'form'})"
        :name "questions-file"
        :type "file"}]]]]
   [:button.btn.btn-primary
    {:data-on:mousedown "@post('/create')"}
    (tr [:create-game])]])

(defmethod game-view [:moderator :new]
  [{:tempura/keys [tr]
    game-id :sid}]
  (let [play-game-url (str (:url config) "/play/" game-id)
        lobby (game/lobby game-id)
        has-enough-players? (game/has-enough-players? game-id)]
    [:div#sections
     (end-game tr)
     [:section#content
      [:div
       [:div#qrcode (url->qrcode-svg play-game-url)]
       [:p#game-url
        [:input
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
      (end-game tr)
      (timer answer-revealed?)
      [:div
       [:p#answer-progress
        [:label
         (tr [:players-answered])
         [:br]
         [:progress
          {:max total
           :value answered}
          answer-progress-text]]]
       [:div#question
        {:data-signals:_audio "el.querySelector('audio')"
         :data-init (if answer-revealed?
                      "$_audio && $_audio.pause()"
                      "$_audio && $_audio.play()")} ; Play any audio if present in the question.
        (:text question)
        (when answer-revealed?
          [:a.replay-audio
           {:data-show "$_audio"
            :data-on:click "$_audio.currentTime = 0; $_audio.play()"
            :title (tr [:replay-audio])}
           [:i.material-icons.md-36 "replay"]])]
       [:i.material-icons.md-36.scoring-icon scoring-icon]
       (answers-view tr
                     true
                     answer-revealed?
                     game-id
                     question)]
      (when answer-revealed?
        [:p (next-button tr "@post('/leaderboard')")])]))

(defmethod game-view [:moderator :question]
  [{:tempura/keys [tr]
    game-id :sid}]
  (question-view tr game-id false))

(defmethod game-view [:moderator :show-answers]
  [{:tempura/keys [tr]
    game-id :sid}]
  (question-view tr game-id true))

(defmethod game-view [:moderator :leaderboard]
  [{:tempura/keys [tr]
    game-id :sid}]
  (if (game/all-questions-answered? game-id)
    [:section#content
     (leaderboard tr game-id)
     [:p
      [:button.btn.btn-primary
       {:data-on:click end-game-cmd
        :data-on:keydown__window (format "evt.key === 'Enter' && %s" end-game-cmd)}
       (tr [:end-game])
       [:i.material-icons.md-light.md-36 "cancel"]]]]
    [:section#content
     (end-game tr)
     (leaderboard tr game-id)
     [:p (next-button tr "@post('/question')")]]))
