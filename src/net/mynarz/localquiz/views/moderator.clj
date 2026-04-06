(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [net.mynarz.localquiz.question-sources :refer [question-sources]]
            [net.mynarz.localquiz.util :refer [decimal-format svg]]
            [net.mynarz.localquiz.views.common :as views]
            [clojure.math :as math]
            [charred.api :as charred]))

(defn answer-progress
  [tr
   ^String game-id]
  (let [{:keys [total answered]} (game/answer-progress game-id)
        answer-progress-text (format "%d/%d" answered total)]
    [:label#answer-progress
     [:span.chip-label (tr [:players-answered])]
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
   [:button.btn
    {:data-on:click "$_endGameDialog.showModal()"}
    [:i.material-icons (svg "cancel.svg")]
    (tr [:end-game])]])

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
                       (apply max))
        final-leaderboard? (= questions-answered questions-total)]
    [:div#leaderboard
     [:table
      [:caption
       [:div (tr [:question/progress] [questions-answered questions-total])]
       [:progress
        {:max questions-total
         :value questions-answered}]]
      [:thead
       [:tr
        [:th]
        [:th (tr [:player])]
        [:th (tr [:score])]
        [:th]]]
      [:tbody
       (for [{:keys [player-name score index]} (map-indexed (fn [index data]
                                                              (assoc data :index (inc index)))
                                                            leaderboard-data)
             :let [score-decimal (decimal-format score)
                   score-style (->> (if (zero? score) score (/ score max-score))
                                    decimal-format
                                    (format "--score: %s;"))]]
         [:tr
          [:td index]
          [:td player-name
           (when (pos? score)
             [:i.score-direction "↑"])
           (when (and final-leaderboard? (= index 1))
             [:i.material-icons (svg "emoji_events.svg")])]
          [:td [:span.score-bar {:style score-style}]]
          [:td score-decimal]])]]]))

(defn next-button
  [tr
   ^String next-action]
  [:button.btn.btn-primary
   {:data-on:click next-action
    :data-on:keydown__window (str "evt.key === 'Enter' && " next-action)}
   (tr [:next])
   [:i.material-icons.md-large (svg "arrow_circle_right.svg")]])

(defn number-of-questions
  [tr]
  [:p.form-group
   {:data-signals:number-of-questions__ifmissing (:default-number-of-questions config)}
   [:label
    {:for "number-of-questions"}
    (tr [:number-of-questions])]
   [:input#number-of-questions
    {:data-bind "numberOfQuestions"
     :min 1
     :name "number-of-questions"
     :type "number"}]])

(defn replay-audio
  [tr]
  [:button.btn#replay-audio
   {:data-show "$_audio"
    :data-on:click "$_audio.currentTime = 0; $_audio.play()"}
   [:i.material-icons (svg "replay.svg")]
   (tr [:replay-audio])])

(defn create-game-form-fields
  [tr]
  [views/lang-input
   [(number-of-questions tr)
    (create-button tr)]])

(defn tab-checkbox
  ([^String id]
   (tab-checkbox id false))
  ([^String id
    ^Boolean checked]
   [:input
    {:checked checked
     :data-on:change (format "$error = false; $numberOfQuestions = %d" (:default-number-of-questions config))
     :id id
     :name "tab-picker"
     :type "radio"}]))

(defn max-upload-size
  "The localized error message if the maximum upload size is exceeded."
  [tr]
  (tr [:errors/max-upload-size-exceeded]
      [(decimal-format (/ (:max-upload-size config) (math/pow 10 6)))]))

(defmethod views/game-view [:moderator nil]
  [{[language & _] :tempura/locales
    :tempura/keys [tr]
    :as request}]
  (views/morph-body
    request
    [:section#content
     [:div.tabs
      (tab-checkbox "select-questions-checkbox" true)
      [:label
       {:for "select-questions-checkbox"}
       (tr [:pick-questions])]
      [:form
       [:p
        [:select#question-picker
         {:data-on:change (views/post "/create/validate")
          :name "question-source"}
         [:option
          {:disabled true
           :selected true}
          (tr [:pick-questions])]
         (for [{:keys [name url]} (question-sources (or language :en))]
           [:option
            {:value url}
            name])]]
       (create-game-form-fields tr)]
      (tab-checkbox "upload-questions-checkbox")
      [:label
       {:for "upload-questions-checkbox"}
       (tr [:upload-questions])]
      [:form
       {:enctype "multipart/form-data"}
       [:p
        [:input#questions-upload
         {:accept ".edn"
          :data-on:change (str (format "evt.target.files[0]?.size < %d ? " (:max-upload-size config))
                               (views/post "/create/validate")
                               (format " : $error = '%s'" (max-upload-size tr)))
          :name "question-file"
          :type "file"}]]
       (create-game-form-fields tr)]]]))

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

(defn question-header
  [tr
   ^String game-id]
  (let [scoring (-> game-id game/current-question :scoring)
        scoring-indicator (if (= scoring :consensus)
                            [:span#venn-conversation
                             [:span.chip-label (tr [:scoring])]
                             (svg "venn_conversation_animated.svg")
                             (tr [:consensus])]
                            [:span#scoring-icon
                             [:span.chip-label (tr [:scoring])]
                             [:i.material-icons (svg "task_alt.svg")]
                             (tr [:correctness])])]
    [(answer-progress tr game-id)
     scoring-indicator]))

(defn question-view
  ([tr
    ^String game-id]
   (question-view tr game-id {}))
  ([tr
    ^String game-id
    {:keys [answer-revealed?]
     :as answers}]
   (let [{:keys [scoring text] :as question} (game/current-question game-id)
         mark-correct? (and answer-revealed? (not= scoring :consensus))]
      [:section#content
       (timer answer-revealed?)
       [:div#question-container
        [:div#question
         {:data-signals:_audio "el.querySelector('audio')"
          :data-init (if answer-revealed?
                       "$_audio && $_audio.pause()"
                       "$_audio && $_audio.play(); typeText(el)")} ; Play any audio if present in the question.
         text]
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
    (question-header tr game-id)
    (end-game tr)
    (question-view tr game-id)))

(defmethod views/game-view [:moderator :show-answers]
  [{:tempura/keys [tr]
    game-id :sid
    :as request}]
  (views/morph-body
    request
    (question-header tr game-id)
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
         [:i.material-icons.md-dark (svg "cancel.svg")]]]
       [:p (next-button tr "@post('/question')")])]))
