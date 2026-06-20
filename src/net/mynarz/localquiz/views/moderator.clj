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
  "Show how many players in `game-id` have already answered the current question."
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
  [{:tempura/keys [tr]}]
  [:button.btn.btn-primary
   {:data-attr:disabled "$_creating || $_validating"
    :data-indicator "_creating"
    :data-on:click (views/post "/create")
    :data-show "$questionsValidated"}
   (tr [:create-game])])

(defn end-game
  [tr]
  [:div#end-game
   [:dialog#end-game-dialog
    {:data-ref "_endGameDialog"}
    [:p (tr [:confirm-end-game])]
    [:p
     [:button.btn
      {:data-on:click "@post('/end')"}
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
                       (map :total-score)
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
       (for [{:keys [index player-name score total-score winner?]} leaderboard-data
             :let [score-style (format "--former-score: %s; --score: %s;"
                                       (decimal-format (/ (- total-score score) max-score))
                                       (decimal-format (/ total-score max-score)))]]
         [:tr
          [:td index]
          [:td player-name
           (when (pos? score)
             [:i.score-direction "↑"])
           (when (and final-leaderboard? winner?)
             [:i.material-icons (svg "emoji_events.svg")])]
          [:td
           {:style score-style}
           [:span.score-bar]]
          [:td (decimal-format total-score)]])]]]))

(defn next-button
  ([tr]
   (next-button tr
                "@post('/next')"
                :next
                [:i.material-icons.md-large (svg "arrow_circle_right.svg")]))
  ([tr
    ^String next-action
    ^clojure.lang.Keyword label-key
    icon]
   [:button.btn.btn-primary
    {:data-on:click next-action
     :data-on:keydown__window (str "evt.key === 'Enter' && " next-action)}
    (tr [label-key])
    icon]))

(defn number-of-questions
  "Input for the number of questions for a game."
  [{{number-of-questions :numberOfQuestions} :signals
    :tempura/keys [tr]}]
  [:p.form-group
   {:data-signals:number-of-questions (or number-of-questions (:default-number-of-questions config))}
   [:label
    {:for "number-of-questions"}
    (tr [:number-of-questions])]
   [:input#number-of-questions
    {:data-bind "numberOfQuestions"
     :min 1
     :name "number-of-questions"
     :type "number"}]])

(defn replay-audio
  "A button to replay audio from the question, if present."
  [tr]
  [:button.btn#replay-audio
   {:data-show "$_audio"
    :data-on:click "$_audio.currentTime = 0; $_audio.play()"}
   [:i.material-icons (svg "replay.svg")]
   (tr [:replay-audio])])

(defn create-game-form-fields
  [request]
  [views/lang-input
   [(number-of-questions request)
    (create-button request)]])

(defn tab-checkbox
  ([^String id]
   (tab-checkbox id false))
  ([^String id
    ^Boolean checked]
   [:input
    {:checked checked
     :data-on:change (format "$error = false; $numberOfQuestions = %d; $questionsValidated = false;"
                             (:default-number-of-questions config))
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
      {:data-signals:questions-validated__ifmissing false}
      (tab-checkbox "select-questions-checkbox" true)
      [:label
       {:for "select-questions-checkbox"}
       (tr [:pick-questions])]
      [:form
       [:p
        [:select#question-picker
         {:data-on:change (views/post "/create/validate")
          :data-indicator "_validating"
          :name "question-source"}
         [:option
          {:disabled true
           :selected true}
          (tr [:pick-questions])]
         (for [{:keys [name url]} (question-sources (or language :en))]
           [:option
            {:value url}
            name])]]
       (create-game-form-fields request)]
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
       (create-game-form-fields request)]]]))

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
            {:data-on:click "@post('/next')"
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
        scoring-indicator (case scoring
                            :consensus
                            [:span#venn-conversation
                             [:span.chip-label (tr [:scoring])]
                             [:i.material-icons (svg "venn_conversation_animated.svg")]
                             (tr [:consensus])]

                            :majority
                            [:span#scoring-icon
                             [:span.chip-label (tr [:scoring])]
                             [:i.material-icons (svg "pacman.svg")]
                             (tr [:majority])]

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
         mark-correct? (and answer-revealed? (nil? scoring))]
      [:section#content
       (timer answer-revealed?)
       [:div#question-container
        [:div#question
         {:data-signals:_audio "el.querySelector('audio')"
          :data-init (if answer-revealed?
                       "$_audio && $_audio.pause()"
                       "$_audio && $_audio.play();")} ; Play any audio if present in the question.
         text]
        (views/answers-view tr
                            true
                            answers
                            game-id
                            mark-correct?
                            question)]
       (when answer-revealed?
         [:p (next-button tr)])])))

(defmethod views/game-view [:moderator :question]
  [{:tempura/keys [tr]
    game-id :sid
    :as request}]
  (views/morph-body
    request
    (question-header tr game-id)
    [(replay-audio tr)
     (end-game tr)]
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
     [:p
      (if (game/all-questions-answered? game-id)
        (next-button tr
                     "@post('/end')"
                     :end-game
                     [:i.material-icons.md-dark (svg "cancel.svg")])
        (next-button tr))]]))
