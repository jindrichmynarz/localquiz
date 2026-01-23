(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [net.mynarz.localquiz.question-sources :refer [question-sources]]
            [net.mynarz.localquiz.util :refer [decimal-format]]
            [net.mynarz.localquiz.views.common :as views]
            [charred.api :as charred]))

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

(defn create-button
  [tr]
  [:button.btn.btn-primary
   {:data-on:click (views/post "/create")}
   (tr [:create-game])])

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

(defn get-answers
  [^String game-id]
  (let [answers (game/get-answers game-id)]
    {:answer-count (count answers)
     :answer-frequencies (->> answers
                           (map :answer)
                           frequencies)
     :answer-revealed? (boolean (seq answers))}))

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

(defn number-of-questions
  [tr]
  [:p
   [:label
    {:for "number-of-questions"}
    (tr [:number-of-questions])]
   [:input#number-of-questions
    {:min 1
     :name "number-of-questions"
     :type "number"
     :value 20}]])

(defmethod views/game-view [:moderator nil]
  [{:keys [error]
    :tempura/keys [tr]}]
  [:section#content
   [:div.tabs
    [:input#select-questions-checkbox
     {:checked true
      :name "tab-picker"
      :type "radio"}]
    [:label
     {:for "select-questions-checkbox"}
     (tr [:pick-questions])]
    [:form
     [:p
      [:select#question-picker
       {:name "question-source"
        :placeholder (tr [:pick-questions])}
       (for [question-source (keys question-sources)]
         [:option
          {:value question-source}
          question-source])]]
     (number-of-questions tr)
     (create-button tr)]
    [:input#upload-questions-checkbox
     {:name "tab-picker"
      :type "radio"}]
    [:label
     {:for "upload-questions-checkbox"}
     (tr [:upload-questions])]
    [:form
     {:enctype "multipart/form-data"}
     [:p
      [:input#questions-upload
       {:accept ".edn"
        :data-on:change (views/post "/create/validate")
        :name "question-file"
        :type "file"}]]
     (number-of-questions tr)
     (if error
       [:pre.error error]
       (create-button tr))]]])

(defmethod views/game-view [:moderator :new]
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
  [answer-revealed?]
  (when-not answer-revealed?
    (let [duration (:question-time-out config)]
      [:div.timer
       {:data-style:--duration (format "'%ds'" duration)}
       [:div]])))

(defn question-view
  ([tr
    ^String game-id]
   (question-view tr game-id []))
  ([tr
    ^String game-id
    {:keys [answer-revealed?]
     :as answers}]
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
             :data-on:click "$_audio.currentTime = 0; $_audio.play()"}
            (tr [:replay-audio])
            [:i.material-icons.md-36 "replay"]])]
        [:i.material-icons.md-36.scoring-icon scoring-icon]
        (views/answers-view tr
                            true
                            answers
                            game-id
                            question)]
       (when answer-revealed?
         [:p (next-button tr "@post('/leaderboard')")])])))

(defmethod views/game-view [:moderator :question]
  [{:tempura/keys [tr]
    game-id :sid}]
  (question-view tr game-id))

(defmethod views/game-view [:moderator :show-answers]
  [{:tempura/keys [tr]
    game-id :sid}]
  (let [answers (get-answers game-id)]
    (question-view tr game-id answers)))

(defmethod views/game-view [:moderator :leaderboard]
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
