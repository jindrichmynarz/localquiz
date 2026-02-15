(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [net.mynarz.localquiz.question-sources :refer [question-sources]]
            [net.mynarz.localquiz.util :refer [decimal-format svg]]
            [net.mynarz.localquiz.views.common :as views]
            [charred.api :as charred]
            [taoensso.timbre :as log]))

(defn answer-progress
  [tr
   ^String game-id]
  (let [{:keys [total answered]} (game/answer-progress game-id)
        answer-progress-text (format "%d/%d" answered total)]
    [:label#answer-progress
     (tr [:players-answered])
     [:progress
      {:max total
       :value answered}
      answer-progress-text]]))

(defn copy-button
  [tr
   ^String join-game-url]
  [:span.copy-button-wrapper
   {:data-signals:_copy-label (charred/write-json-str [(tr [:copy]) (tr [:copied])])}
   [:button.btn#copy-join-url
    {:data-on:mousedown (format "navigator.clipboard.writeText('%s');
                                 $_copyLabel.reverse();
                                 setTimeout(() => $_copyLabel.reverse(), 2000);"
                                join-game-url)
     :data-text "$_copyLabel[0]"}]])

(defn create-button
  [tr]
  [:button.btn.btn-primary
   {:data-on:click (views/post "/create")}
   (tr [:create-game])])

(def end-game-cmd
  "@post('/end')")

(defn end-game
  [tr]
  [:div#end-game
   [:dialog#end-game-dialog
    {:data-ref "_endGameDialog"}
    [:p (tr [:confirm-end-game])]
    [:p
     [:button.btn
      {:data-on:click end-game-cmd}
      (tr [:question.yesno/yes])]
     [:button.btn
      {:data-on:click "$_endGameDialog.close()"}
      (tr [:question.yesno/no])]]]
   [:button
    {:data-on:click "$_endGameDialog.showModal()"
     :title (tr [:end-game])}
    [:i.material-icons (svg "cancel.svg")]]])

(defn get-answers
  [^String game-id]
  (let [answers (game/get-answers game-id)]
    {:answer-count (count answers)
     :answer-frequencies (->> answers
                              (map :answer)
                              frequencies)}))

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
       [:div (tr [:question/progress] [questions-answered questions-total])]
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

(defn next-button
  [tr
   ^String next-action]
  [:button.btn.btn-primary
   {:data-on:click next-action
    :data-on:keydown__window (str "evt.key === 'Enter' && " next-action)}
   (tr [:next])
   [:i.material-icons (svg "arrow_circle_right.svg")]])

(defn number-of-questions
  [tr]
  [:p.form-group
   [:label
    {:for "number-of-questions"}
    (tr [:number-of-questions])]
   [:input#number-of-questions
    {:min 1
     :name "number-of-questions"
     :type "number"
     :value 20}]])

(defn replay-audio
  [tr]
  [:a#replay-audio
   {:data-show "$_audio"
    :data-on:click "$_audio.currentTime = 0; $_audio.play()"}
   (tr [:replay-audio])
   [:i.material-icons (svg "replay.svg")]])

(defmethod views/game-view [:moderator nil]
  [{:keys [error]
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
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
       views/lang-input
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
       views/lang-input
       (number-of-questions tr)
       (if error
         [:pre.error error]
         (create-button tr))]]]))

(defmethod views/game-view [:moderator :new]
  [{:tempura/keys [tr]
    game-id :sid
    :as request}]
  (views/morph-body
    request
    (end-game tr)
    (let [play-game-url (str (:url config) "/play/" game-id)
          lobby (game/lobby game-id)
          has-enough-players? (game/has-enough-players? game-id)]
      [:div#sections
       [:section#content
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
            (svg "wifi_exercise_animated.svg")
            (tr [:wait-for-players])])]
       (when (seq lobby)
         [:section#lobby
          [:table
           [:thead [:tr [:th (tr [:players])]]]
           [:tbody
            (for [player-name lobby]
              [:tr [:td player-name]])]]])])))

(defn timer
  [^Boolean answer-revealed?]
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
         scoring-icon (if (= scoring :consensus)
                         [:span#venn-conversation
                          (svg "venn_conversation_animated.svg")]
                         [:i.material-icons#scoring-icon
                          {:aria-label (tr [:correctness])
                           :title (tr [:correctness])}
                          (svg "task_alt.svg")])
         mark-correct? (and answer-revealed? (not= scoring :consensus))]
      [:section#content
       (timer answer-revealed?)
       [:div#question-container
        [:div#question-menu
         (answer-progress tr game-id)
         scoring-icon]
        [:div#question
         {:data-signals:_audio "el.querySelector('audio')"
          :data-init (if answer-revealed?
                       "$_audio && $_audio.pause()"
                       "$_audio && $_audio.play()")} ; Play any audio if present in the question.
         (:text question)]
        (views/answers-view tr
                            true
                            answers
                            game-id
                            mark-correct?
                            question)]
       (when answer-revealed?
         [:p (next-button tr "@post('/leaderboard')")])])))

(defmethod views/game-view [:moderator :question]
  [{:tempura/keys [tr]
    game-id :sid
    :as request}]
  (views/morph-body
    request
    (end-game tr)
    (question-view tr game-id)))

(defmethod views/game-view [:moderator :show-answers]
  [{:tempura/keys [tr]
    game-id :sid
    :as request}]
  (views/morph-body
    request
    [(replay-audio tr)
     (end-game tr)]
    (let [answers (-> game-id
                      get-answers
                      (assoc :answer-revealed? true))]
      (question-view tr game-id answers))))

(defmethod views/game-view [:moderator :leaderboard]
  [{:tempura/keys [tr]
    game-id :sid
    :as request}]
  (views/morph-body
    request
    (end-game tr)
    [:section#content
     (leaderboard tr game-id)
     (if (game/all-questions-answered? game-id)
       [:p
        [:button.btn.btn-primary
         {:data-on:click end-game-cmd
          :data-on:keydown__window (format "evt.key === 'Enter' && %s" end-game-cmd)}
         (tr [:end-game])
         [:i.material-icons (svg "cancel.svg")]]]
       [:p (next-button tr "@post('/question')")])]))
