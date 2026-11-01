(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [net.mynarz.localquiz.question-sources :refer [question-sources]]
            [net.mynarz.localquiz.util :refer [decimal-format fraction-of score-format svg]]
            [net.mynarz.localquiz.views.common :as views]
            [clojure.math :as math]))

(defn answer-progress
  "Show how many players in `game-id` have already answered the current question."
  [tr
   ^String game-id]
  (let [{:keys [total answered]} (game/answer-progress game-id)]
    [:label#answer-progress
     [:span.chip-label (tr [:players-answered])]
     [:progress
      {:max total
       :value answered}]
     [:span (format "%d/%d" answered total)]]))

(defn copy-button
  [tr
   ^String join-game-url]
  [:copy-button
   {:text join-game-url
    :copy-label (tr [:copy])
    :copied-label (tr [:copied])
    ;; Morphing would empty what the component renders into itself.
    :data-ignore-morph ""}
   ;; Slotted into the component's button.
   [:i.material-icons (svg "content_copy.svg")]
   (tr [:copy])])

(def form-validity-handlers
  "Datastar attributes that keep the $_formValid signal in sync with the form's
  built-in (Constraint Validation API) validity. `el` is the form element."
  {:data-on:input "$_formValid = el.checkValidity()"
   :data-on:change "$_formValid = el.checkValidity()"})

(def ^:private ready
  "Whether the questions are valid, so that a game can be created from them."
  "$questionsValidated && $_formValid")

(defn- crowded?
  "Whether `players` sit on two rings around the fire, which beats one ring from this many."
  [players]
  (>= (count players) 16))

(defn- seat-slots
  "How many places there are around the fire for `players`. Two rings take turns, so they
  need an even number of places. An odd number of players leaves the last one empty, which
  the next player takes without anyone moving."
  [players]
  (let [n (count players)]
    (if (crowded? players)
      (+ n (mod n 2))
      n)))

(defn seats
  "Places for `players` around the fire, clockwise from the top: `:cos` and `:sin` of each
  one's angle, its `:tilt` in degrees and the `:grit-x` and `:grit-y` offsets of its
  texture in pixels, so that no two discs look alike. Rounded so that the markup, and so the
  view hash, is the same on every render. An even number of places leaves the top one free,
  where the smoke rises."
  [players]
  (let [n (seat-slots players)
        offset (if (even? n) (/ math/PI n) 0)
        rounded #(/ (math/round (* % 1000)) 1000.0)]
    (map-indexed (fn [index player]
                   (let [angle (+ (/ math/PI -2) (* index (/ (* 2 math/PI) n)) offset)]
                     {:cos (rounded (math/cos angle))
                      :grit-x (mod (* index 131) 512)
                      :grit-y (mod (* index 197) 512)
                      :player player
                      :sin (rounded (math/sin angle))
                      :tilt (rounded (* 0.8 (- (mod (+ (* index 5) 3) 9) 4)))}))
                 players)))

(defn create-button
  "Button to create a game, hidden until the questions are valid. Loading them and creating
  the game can take a while, so a spinner shows meanwhile: on its own where the button is
  to appear, then in the button."
  [{:tempura/keys [tr]}]
  (list
    [:span.spinner
     {:aria-hidden "true"
      :data-show (str "$_validating && !(" ready ")")}]
    [:button.fire-key
     ;; A string, as ARIA reads the empty attribute that Datastar renders for true as false.
     {:data-attr:aria-busy "String($_validating || $_creating)"
      :data-attr:disabled "$_validating || $_creating"
      :data-indicator "_creating"
      :data-on:click (views/post "/create")
      :data-show ready}
     [:span.spinner
      {:aria-hidden "true"
       :data-show "$_validating || $_creating"}]
     (tr [:create-game])]))

(defn end-game
  [tr
   ^String game-id]
  (views/confirm-button tr
                        :end-game
                        :confirm-end-game
                        (format "@post('/end/%s')" game-id)))

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
        ;; Spans the bar and the number, both of which show the score.
        [:th {:colspan 2} (tr [:score])]]]
      [:tbody
       (for [{:keys [index player-name score total-score winner?]} leaderboard-data
             :let [score-style (format "--former-score: %s; --score: %s;"
                                       (decimal-format (fraction-of (- total-score score) max-score))
                                       (decimal-format (fraction-of total-score max-score)))]]
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
          [:td (score-format total-score)]])]]]))

(defn next-button
  ([tr
    ^String game-id]
   (next-button tr
                (str "@post('/next/" game-id "')")
                :next
                [:i.material-icons.md-large (svg "arrow_circle_right.svg")]))
  ([tr
    ^String next-action
    ^clojure.lang.Keyword label-key
    icon]
   [:button.btn.btn-primary.btn-next
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

(defn replay-media
  "A button to replay audio or video from the question, if present."
  [tr]
  [:button.btn#replay-media
   {:data-show "$_media"
    :data-on:click "$_media.currentTime = 0; $_media.play()"}
   [:i.material-icons (svg "replay.svg")]
   (tr [:replay-media])])

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
      {:data-signals:questions-validated__ifmissing false
       :data-signals:_form-valid__ifmissing "true"}
      (tab-checkbox "select-questions-checkbox" true)
      [:label
       {:for "select-questions-checkbox"}
       (tr [:pick-questions])]
      [:form
       form-validity-handlers
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
       (merge {:enctype "multipart/form-data"} form-validity-handlers)
       [:p
        [:input#questions-upload
         {:accept ".edn"
          :data-on:change (str (format "evt.target.files[0]?.size < %d ? " (:max-upload-size config))
                               (views/post "/create/validate")
                               (format " : $error = '%s'" (max-upload-size tr)))
          :data-indicator "_validating"
          :name "question-file"
          :type "file"}]]
       (create-game-form-fields request)]]]))

(defmethod views/game-view [:moderator :new]
  [{:tempura/keys [tr]
    {:keys [game-id]} :path-params
    :as request}]
  (views/morph-body
    request
    (end-game tr game-id)
    (let [play-game-url (str (:url config) "/play/" game-id)
          lobby (game/lobby game-id)
          has-enough-players? (game/has-enough-players? game-id)]
      [:div#lobby-grid
       ;; Outside of the fire's stage, which would contain it. Morphing would clear it.
       (when has-enough-players?
         [:canvas.fire-sky
          {:aria-hidden "true"
           :data-ignore-morph ""}])
       [:section#join
        [:h2 (tr [:scan-to-join])]
        [:p.hint (tr [:point-camera])]
        [:div#qrcode (url->qrcode-svg play-game-url)]
        [:label.hint
         {:for "join-url"}
         (tr [:or-open-link])]
        [:p#game-url
         [:input#join-url
          {:readonly true
           :type "text"
           :value play-game-url}]
         (copy-button tr play-game-url)]]
       [:section#lobby
        [:h2 (tr [:players])]
        [:div#campfire
         {:class [(when has-enough-players? "lit")
                  (when (crowded? lobby) "crowded")]
          :style (format "--players:%d;--slots:%d" (count lobby) (seat-slots lobby))}
         ;; Cold logs from the start, which catch fire once the game can start.
         [:div.fire-scene
          {:aria-hidden "true"}
          (svg "campfire_soot.svg")
          (svg "campfire_logs.svg")
          (when has-enough-players?
            (list
              [:div.fire-glow]
              [:camp-fire
               ;; Morphing would empty what the component renders into itself.
               {:data-ignore-morph ""}]))]
         [:ul
          (for [{:keys [cos grit-x grit-y player sin tilt]} (seats lobby)]
            [:li
             ;; Lets the seat keep its element, and so glide, when a player before it leaves.
             {:id (str "seat-" (hash player))
              :style (format "--cos:%s;--sin:%s;--tilt:%sdeg;background-position:%dpx %dpx"
                             cos sin tilt grit-x grit-y)}
             [:span player]])]]
        ;; Hidden rather than left out, so that the fire does not move when it appears.
        [:div#start-game
         [:button.fire-key
          {:data-on:click (str "@post('/next/" game-id "')")
           :disabled (not has-enough-players?)
           :style (when-not has-enough-players? "visibility: hidden")
           :type "submit"}
          (tr [:start-game])]]]])))

(defn question-header
  [tr
   ^String game-id
   {:keys [scoring]}]
  (let [scoring-indicator (case scoring
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
  [tr
   ^String game-id
   {:keys [asked-at scoring text] :as question}
   {:keys [answer-revealed?]
    :as answers}]
  (let [mark-correct? (and answer-revealed? (nil? scoring))]
    [:section#content
     (views/timer asked-at answer-revealed?)
     [:div#question-container
      [:div#question
       {:data-signals:_media "el.querySelector('audio, video')"
        :data-init (if answer-revealed?
                     "$_media && $_media.pause()"
                     "$_media && $_media.play();")} ; Play any audio or video if present in the question.
       text]
      (views/answers-view tr
                          true
                          answers
                          game-id
                          mark-correct?
                          question)]
     (when answer-revealed?
       [:p (next-button tr game-id)])]))

(defmethod views/game-view [:moderator :question]
  [{:tempura/keys [tr]
    {:keys [game-id]} :path-params
    :as request}]
  (let [question (game/current-question game-id)]
    (views/morph-body
      request
      (question-header tr game-id question)
      [(replay-media tr)
       (end-game tr game-id)]
      (question-view tr game-id question {}))))

(defmethod views/game-view [:moderator :show-answers]
  [{:tempura/keys [tr]
    {:keys [game-id]} :path-params
    :as request}]
  (let [question (game/current-question game-id)
        answers (-> game-id
                    get-answers
                    (assoc :answer-revealed? true))]
    (views/morph-body
      request
      (question-header tr game-id question)
      [(replay-media tr)
       (end-game tr game-id)]
      (question-view tr game-id question answers))))

(defmethod views/game-view [:moderator :leaderboard]
  [{:tempura/keys [tr]
    {:keys [game-id]} :path-params
    :as request}]
  (views/morph-body
    request
    (end-game tr game-id)
    [:section#content
     (leaderboard tr game-id)
     [:p
      (if (game/all-questions-answered? game-id)
        (next-button tr
                     "$_confirmDialog.showModal()"
                     :end-game
                     [:i.material-icons.md-dark (svg "cancel.svg")])
        (next-button tr game-id))]]))
