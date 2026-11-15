(ns net.mynarz.localquiz.views.common
  (:require [net.mynarz.localquiz.actions.common :refer [refresh-session!]]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.headers :as headers]
            [net.mynarz.localquiz.network :as network]
            [net.mynarz.localquiz.normalize :refer [normalize-answer]]
            [net.mynarz.localquiz.question-spec :as qs]
            [net.mynarz.localquiz.session :as session]
            [net.mynarz.localquiz.util :as util :refer [svg]]
            [net.mynarz.localquiz.views.network :refer [network-map result-tree]]
            [charred.api :as charred]
            [clojure.string :as string]
            [dev.onionpancakes.chassis.compiler :as cc]
            [dev.onionpancakes.chassis.core :as h]
            [starfederation.datastar.clojure.brotli :as brotli])
  (:import (java.text Collator)
           (java.util Locale)))

; Warn on ambiguous attributes
(cc/set-warn-on-ambig-attrs!)

(defn init-js
  ;; Retry Infinity means we always try to reconnect. The other defaults
  ;; mean that this will at most take 30s (default max backoff).
  [^String game-id]
  (let [endpoint (cond-> "/sse"
                    game-id (str "/" game-id))]
    (format "@get('%s', {retryMaxCount: Infinity})" endpoint)))

(defn lang-switch-input
  []
  [:input.offscreen#lang-toggle
   {:data-attr:checked "$language == 'en'"
    :data-ignore-morph "" ; Avoid the checked attribute to be reset.
    :data-on:change "$language = $language == 'cs' ? 'en' : 'cs';
                     localStorage.setItem('language', $language);
                     location.reload()"
    :type "checkbox"}])

(defn night-switch
  "Sets day or night by hand, over the clock, for the rest of the session in this tab."
  [tr]
  [:p#night-switch.toggle
   [:span (svg "sun.svg")]
   [:input.offscreen#night-toggle
    {:data-attr:checked "!$_day"
     :data-ignore-morph "" ; Avoid the checked attribute to be reset.
     :data-on:change "$_day = !$_day; sessionStorage.setItem('day', $_day)"
     :type "checkbox"}]
   [:label.switch
    {:for "night-toggle"
     :title (tr [:switch-night])}]
   [:span (svg "moon.svg")]])

(defn lang-switch
  [tr]
  [:p#lang-switch.toggle
   [:span "CS"]
   (lang-switch-input)
   [:label.switch
    {:for "lang-toggle"
     :title (tr [:switch-lang])}]
   [:span "EN"]])

(def lang-input
  [:input
   {:data-attr:value "$language"
    :name "language"
    :type "hidden"}])

(defn footer
  [tr]
  [:footer
   [:p
    (interpose
      " "
      [(tr [:footer/made-by])
       [:a {:href "https://mynarz.net/#jindrich"} "Jindřich Mynarz"]
       (tr [:footer/with])
       [:abbr {:title (tr [:footer/persistence])} "♥\uFE0E"]
       (tr [:footer/using])
       [:a {:href "https://clojure.org"} "Clojure"]
       (tr [:and])
       [:a {:href "https://data-star.dev"} "Datastar"]
       "🚀."])]])

(defn confirm-button
  "Button labelled `label` that asks `question` in a dialog and runs `action` on yes. At most
  one on a page, which can open the dialog elsewhere too, with $_confirmDialog."
  [tr
   label
   question
   ^String action]
  [:div#confirm
   [:dialog#confirm-dialog
    {:data-ref "_confirmDialog"
     ;; Morphing would remove `open`, closing it but leaving the page inert under it.
     :data-ignore-morph ""}
    [:h2 (tr [question])]
    [:p
     [:button.fire-key.unlit
      {:data-on:click action}
      (tr [:question.yesno/yes])]
     ;; The harmless answer gets the focus, so that Enter does not run the action.
     [:button.fire-key.unlit
      {:autofocus true
       :data-on:click "$_confirmDialog.close()"}
      (tr [:question.yesno/no])]]]
   [:button.key
    {:data-on:click "$_confirmDialog.showModal()"}
    [:i.material-icons (svg "cancel.svg")]
    (tr [label])]])

(defn fire-scene
  "Logs of the fire, to go inside `#campfire`, in one of these states:
  - `:cold`: Only the logs.
  - `:lit`: Burning.
  - `:out`: `<camp-fire>` is kept, as `#campfire.out` hides its flames but not its smoke."
  [state]
  [:div.fire-scene
   {:aria-hidden "true"}
   (svg "campfire_soot.svg")
   (svg "campfire_logs.svg")
   (when (= state :lit)
     [:div.fire-glow])
   (when-not (= state :cold)
     [:camp-fire
      ;; Morphing would empty what the component renders into itself.
      {:data-ignore-morph ""}])])

(defn error-dialog
  [tr]
  [:div#error-dialog
   {:aria-live "assertive"
    :data-on:keydown__window "evt.key === 'Escape' && ($error = '', $errorPreformatted = false)"
    :data-show "$error"
    :role "dialog"
    ;; Hidden until Datastar loads, or the page would open with the dialog showing.
    :style "display: none"}
   [:h2 (tr [:errors/errors])]
   [:p {:data-show "!$errorPreformatted"
        :data-text "$error"}]
   [:pre {:data-show "$errorPreformatted"
          :data-text "$error"}]
   [:button.fire-key
    {:data-on:click "$error = ''; $errorPreformatted = false"}
    (tr [:close])]])

(declare ->session-role)

(defn morph-body
  ([request]
   (morph-body request nil nil nil))
  ([request main]
   (morph-body request nil nil main))
  ([request header main]
   (morph-body request nil header main))
  ([{:tempura/keys [tr]
     :as request}
    center
    header
    main]
   ;; The moderator's view of a running game is projected and read across a room, so it
   ;; gets a type scale that keeps growing with the display (see --font-size-projected in
   ;; style.css). The create form is moderator-only too, but it is filled in at a keyboard,
   ;; so it keeps the hand-held scale. The credits are for the home page: during a game
   ;; they would only take the line the answers need.
   (let [game-id (-> request :path-params :game-id)
         projected? (boolean (and (= :moderator (->session-role request)) game-id))]
     [:div#morph
      {:class (when projected? "projected")
       :data-signals:error__ifmissing ""
       :data-signals:error-preformatted__ifmissing false}
      [:header
       [:h1
        [:a
         {:href "/"}
         "Localquiz"]]
       [:div#header-center center]
       [:div#menu-trigger
        {:data-signals:_menu-open "false"}
        [:div#top-menu
         {:data-class:open "$_menuOpen"}
         header
         (lang-switch tr)
         (night-switch tr)]
        [:button.key
         {:aria-label (tr [:menu])
          :data-on:click "$_menuOpen = !$_menuOpen"
          :type "button"}
         [:i.material-icons (svg "menu.svg")]]]]
      [:main main]
      (when-not game-id (footer tr))
      (error-dialog tr)])))

(def ^:private birds
  "The birds crossing the sky of the day: two small flocks and one on its own, from a fixed
  seed. Each course has a height in % of the sky, seconds to cross, a start, a
  direction, and the swoops of its path: how far it strays up or down, in % of the sky, at
  each fifth of the way. The birds of a flock share their course, each trailing the one
  before it by a little and at its own height. Each bird has its own size, half a wing beat,
  how far into its flapping and gliding it starts, and how it banks into the swoops."
  (let [random (java.util.Random. 5)
        between (fn [lo hi] (+ lo (* (.nextDouble random) (- hi lo))))
        int-between (fn [lo hi] (+ lo (.nextInt random (int (inc (- hi lo))))))
        ; Rounded, so that the styles stay short.
        round (fn [x] (/ (Math/round (* 10.0 x)) 10.0))
        course (fn []
                 {:y (between 10 32)
                  :duration (int-between 60 100)
                  :delay (- (between 0 100))
                  :reverse? (< (.nextDouble random) 0.35)
                  :swoops (vec (repeatedly 4 #(int-between -8 10)))})
        bird (fn [course size]
               (-> course
                   (update :y round)
                   (update :delay round)
                   (assoc :size (round size)
                          :beat (round (between 0.7 1))
                          :phase (round (- (between 0 10)))
                          :banks (vec (repeatedly 4 #(int-between -8 8))))))]
    (vec (concat (for [_ (range 2)
                       :let [flock (course)
                             size (between 0.6 0.95)
                             trails (reductions + 0 (repeatedly (dec (int-between 2 4))
                                                                #(between 0.5 1.3)))]
                       trail trails]
                   (bird (-> flock
                             (update :delay - trail)
                             (update :y + (between -3 3)))
                         (* size (between 0.9 1.1))))
                 [(bird (course) (between 0.65 1))]))))

(def night
  "The sky and the ground behind every screen. Outside of the morphed part of the page, as
  it never changes. The stars come from a fixed seed, so that each page load draws the same
  sky."
  (let [random (java.util.Random. 7)
        pick #(.nextInt random (int %))]
    [:div#night
     {:aria-hidden "true"}
     (vec (repeatedly 46 (fn []
                           [:i.star
                            {:style (str "left:" (pick 100) "%;--y:" (pick 60) ";"
                                         "--size:" (inc (pick 2)) "px;"
                                         "animation-duration:" (+ 5 (pick 7)) "s;"
                                         "animation-delay:-" (pick 11) "s")}])))
     ; Before the horizon, so that they pass behind the mountains.
     (for [[left y angle duration delay] [[8 8 8 47 9]
                                          [38 14 5 61 31]
                                          [62 5 10 83 58]]]
       [:i.comet
        {:style (format "left:%d%%;--y:%d;rotate:%ddeg;animation-duration:%ds;animation-delay:%ds"
                        left y angle duration delay)}])
     ; The flapping cycle is eight half beats, 60% of it, then the glide. Numbers go in with
     ; %s, as %f would follow the locale's decimal comma.
     (for [{:keys [y size duration delay beat phase reverse? swoops banks]} birds]
       [:i.bird
        {:style (str (format "--y:%s;--size:%s;--fly:%ss;--delay:%ss;--dir:%s;--cycle:%ss;--phase:%ss"
                             y size duration delay (if reverse? "reverse" "normal")
                             (/ (Math/round (/ beat 0.0075)) 10.0) phase)
                     (apply str (map-indexed (fn [i m] (format ";--m%d:%s" (inc i) m)) swoops))
                     (apply str (map-indexed (fn [i r] (format ";--r%d:%sdeg" (inc i) r)) banks)))}])
     [:div#dawn
      [:i]]
     ; Two ridges, so that the mist of the day can lie between them.
     (svg "horizon-far.svg")
     [:i.mist.mist-far]
     [:i.mist.mist-mid]
     (svg "horizon-near.svg")
     [:div.grain]
     [:i.mist.mist-near]]))

(def ^:private day-js
  "Whether it is day: as the night switch was set in this tab, else by the browser's local
  time."
  "sessionStorage.getItem('day') ? sessionStorage.getItem('day') === 'true' : (h => h >= 6 && h < 18)(new Date().getHours())")

(defn shim-page
  "A basic HTML page with Datastar setup."
  [{{:keys [game-id]} :path-params
    :tempura/keys [tr]
    :as request}]
  [h/doctype-html5
   [:html
    {:data-attr:lang "$language"
     :data-class "{day: $_day, night: !$_day}"}
    [:head
     [:title "Localquiz"]
     [:meta
      {:charset "UTF-8"}]
     [:link
      {:href "/favicon.svg"
       :rel "icon"
       :type "image/svg+xml"}]
     [:link#css
      {:href "/css/style.css"
       :rel "stylesheet"
       :type "text/css"}]
     [:script
      {:type "importmap"}
      (h/raw headers/importmap)]
     [:link
      {:as "script"
       :href "/js/components.js"
       :rel "modulepreload"}]
     [:script
      {:crossorigin "anonymous"
       :defer true
       :src headers/datastar-url
       :type "module"}]
     [:script
      {:src "/js/components.js"
       :type "module"}]
     ; Enables responsiveness on mobile devices
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1.0"}]]
    [:body {:data-signals:csrf session/csrf-cookie-js
            :data-signals:language "localStorage.getItem('language') || navigator.language.slice(0, 2)"
            ; ponytail: fixed 6–18 h window; compute sunrise and sunset from the location if
            ; the seasons matter.
            :data-signals:_day day-js
            :data-on-interval__duration.60s (str "$_day = " day-js)
            :data-init (init-js game-id)
            ; Reconnect when the user comes online after
            ; being offline. Closes any existing connection
            ; from this element.
            :data-on:online__window (init-js game-id)}
     [:noscript (tr [:no-js])]
     night
     (morph-body request)]]])

(defn view
  "Convert Hiccup `response` to a Ring HTTP response."
  [response]
  (if (vector? response)
    ; Hiccup content
    (let [body (h/html response)]
       {:status 200
        :headers (merge headers/default-headers
                        {"Content-Encoding" "br"
                         "ETag" (crypto/digest body)})
        :body (brotli/compress body :quality 3)})
    ; No content
    {:headers {"Strict-Transport-Security" headers/strict-transport
               "Cache-Control" "no-store"}
     :status 204}))

(def shim-view
  (comp view shim-page))

(defn ->session-role
  "Session role: moderator when there is no game in the URL (the create form) or
  the session owns the game; otherwise player. Reuses the role `morph-view` found, as
  the views ask for it again."
  [{{:keys [game-id]} :path-params
    {:keys [session-role]} :game
    session-id :sid}]
  (cond session-role session-role
        (or (nil? game-id) (game/moderator? game-id session-id)) :moderator
        :else :player))

(defn timer
  "Countdown bar for the current question, offset by the time since it was asked at
  `asked-at`, so that it stays in sync on reloads and for players who join mid-question.
  The browser computes the offset, which keeps the markup the same for the whole question,
  so that re-renders do not change the view hash and are not sent."
  [duration
   ^long asked-at
   ^Boolean answer-revealed?]
  (when-not answer-revealed?
    [:div.timer
     {:data-style:--duration (format "'%ds'" duration)}
     [:div
      ;; Capped at 0, so that a client clock behind the server's does not delay the start.
      ;; Preserved, as a morph removing the style would re-run the expression on the
      ;; running animation, counting the elapsed time twice.
      {:data-preserve-attr "style"
       :data-style:animation-delay (format "Math.min(0, %d - Date.now()) + 'ms'" asked-at)}]]))

(defmulti game-view
  (juxt ->session-role (comp :state :game)))

(defn morph-view
  [{{:keys [game-id]} :path-params
    session-id :sid
    :as request}]
  (let [role (->session-role request)
        state (when game-id (game/get-game-state game-id))
        game {:session-role role
              :state state}]
    ; If a player is not in a running game, redirect to the home page. Not once the
    ; game is gone: its players stay for the closing screen.
    (when (and (= role :player)
               (not (contains? #{nil :new} state))
               (not (game/player-in-game? game-id session-id)))
      (refresh-session! request {:redirect "/"}))
    (game-view (assoc request :game game))))

(defn post
  ([^String endpoint]
   (post endpoint ""))
  ([^String endpoint
    ^String additional-params]
   (format "@post('%s', {contentType: 'form', headers: {'X-Csrf-Token': $csrf}, %s})"
           endpoint
           additional-params)))

(defn answer-handler
  [^String game-id]
  (post (str "/answer/" game-id)))

(defn answer-form-handler
  [^Boolean disabled?
   ^String game-id]
  (when-not disabled?
    {:data-on:click (str "evt.target.tagName == 'INPUT' &&" (answer-handler game-id))}))

(defn submit-button
  [tr
   ^String game-id]
  [:button.fire-key#submit
   {:data-on:click (answer-handler game-id)}
   (tr [:submit])])

(defn mark-answer
  [tr
   ^Boolean answer-revealed?
   ^Boolean correct?]
  (when (and answer-revealed? correct?)
    [:i.material-icons
     {:aria-label (tr [:correct])
      :role "img"}
     (svg "check.svg")]))

(defn note-view
  [answers
   note]
  (when (and answers note)
    [:div.note
     [:div
      [:i.material-icons
       {:aria-hidden "true"}
       (svg "info.svg")]]
     [:div note]]))

(defn add-index
  [coll]
  (map-indexed (fn [index item] (assoc item :index index)) coll))

(defn- frequency-bar
  "How many of `answer-count` answers were given `frequency` times, as a bar large enough to
  read off a projection."
  [answer-count
   frequency]
  [:div.answer-frequency
   [:progress
    {:max answer-count
     :value frequency}]])

(defn answer-frequency
  [{:keys [answer-count
           answer-frequencies
           answer-revealed?]}
   answer]
  (when-let [frequency (and answer-revealed? (get answer-frequencies answer))]
    (frequency-bar answer-count frequency)))

(defn open-answers
  [tr
   {:keys [answer-count
           answer-frequencies
           answer-revealed?]}]
  (when (and answer-revealed? (pos? answer-count))
    [:table#open-answers
     [:caption (tr [:most-common-answers])]
     [:tbody
      (for [[answer frequency] (->> answer-frequencies
                                    (sort-by val util/descending-order)
                                    (take 10))]
        [:tr
         [:td answer]
         [:td (frequency-bar answer-count frequency)]])]]))

(defn revealed-answer
  "The correct answer, shown on its own in a card."
  [answer]
  [:div.answer.revealed
   [:p answer [:i.material-icons (svg "check.svg")]]])

(defn option-label
  "The label of a choice `option`: :text in a multiple-choice option, :label in a
  labelled one (see ::choice in question-spec)."
  [option]
  (or (:text option) (:label option)))

(def ^:private ^Collator collator
  "Orders labels as a reader expects, letters with diacritics next to the ones without.
  ponytail: root locale, so the Czech ch, sorted after h, is not; per-language collators
  if that matters."
  (Collator/getInstance Locale/ROOT))

(defn search-options
  "The `options` whose label contains `fragment`, compared with case, punctuation and
  diacritics normalized away, as :open answers are. Options whose label starts with the
  fragment come first, each group in alphabetical order. All `options`, in alphabetical
  order, when `fragment` is empty (the player asked to see them), nil when it is missing
  (the player has not opened the list)."
  [options
   ^String fragment]
  (when (some? fragment)
    (if-some [needle (some-> fragment
                             normalize-answer
                             not-empty)]
      (->> options
           (sort-by option-label collator)
           (keep (fn [option]
                   (when-some [index (-> option
                                         option-label
                                         normalize-answer
                                         (string/index-of needle))]
                     ; A prefix match is at index 0, so false sorts it before the rest.
                     [(pos? index) option])))
           (sort-by first)
           (map second))
      (sort-by option-label collator options))))

(defn autocomplete
  "Autocomplete text input feeding the enclosing answer form's `answer` field,
  with a custom, stylable suggestion list rendered below the input instead of a
  native (unstylable) `<datalist>`. `options` are filtered server-side by the
  fragment the player has typed, stored in :session/search-fragment for
  `session-id` and retracted when the game moves on to the next question. Revealing all
  the options empties the fragment, which is what tells the server to stop filtering.
  `opts` may give the `:pick` expression run on the suggestion picked, which by default
  submits it as the answer, and `:input` attributes merged into the input."
  ([tr game-id session-id options]
   (autocomplete tr game-id session-id options {}))
  ([tr game-id session-id options opts]
   (let [matches (search-options options (game/get-search-fragment session-id))
         ; queueMicrotask() is needed to propagate the $autocomplete signal as the value of the answer form field.
         pick    (or (:pick opts)
                     (format "$autocomplete = el.dataset.option; queueMicrotask(() => %s)"
                             (answer-handler game-id)))
         reveal  "($autocomplete = '', $_active = -1, $_closed = false, @post('/autocomplete'))"
         ; Focus stays in the input, which points at the highlighted option by aria-activedescendant.
         ; Enter always prevents the implicit submission of the enclosing form.
         ; The arrows reopen a list a pick closed, and scroll once it shows again, which
         ; Datastar applies only after this handler: hidden, it would not scroll.
         navigate (str "['ArrowDown', 'ArrowUp'].includes(evt.key) && ($_closed = false);"
                       "evt.key === 'ArrowDown' && (evt.preventDefault(), el.dataset.options > 0"
                       " ? $_active = Math.min($_active + 1, el.dataset.options - 1)"
                       " : !$autocomplete && " reveal ");"
                       "evt.key === 'ArrowUp' && (evt.preventDefault(), $_active = Math.max($_active - 1, 0));"
                       "['ArrowDown', 'ArrowUp'].includes(evt.key)"
                       " && queueMicrotask(() => document.getElementById('option-' + $_active)?.scrollIntoView({block: 'nearest'}));"
                       "evt.key === 'Enter' && (evt.preventDefault(), document.getElementById('option-' + $_active)?.click())")]
     [:div.autocomplete
      {:data-signals:_active__ifmissing "-1"
       ; A pick that does not submit the answer, and so leaves the view as it is, closes
       ; the list by this.
       :data-signals:_closed__ifmissing "false"}
      [:input
       (merge
        {:aria-autocomplete "list"
         :aria-controls "autocomplete-list"
         :aria-expanded (str (boolean (seq matches)))
         ; Closed by a pick that leaves the view as it is, which the server does not see.
         :data-attr:aria-expanded (format "String(%b && !$_closed)" (boolean (seq matches)))
         :autocomplete "off"
         :autofocus true
         :data-attr:aria-activedescendant "$_active >= 0 && 'option-' + $_active"
         :data-bind "autocomplete"
         :data-init "$autocomplete = ''; $_active = -1" ; Reset
         :data-on:input__debounce.200ms "$_active = -1; $_closed = false; @post('/autocomplete')"
         :data-on:keydown navigate
         :data-options (count matches)
         :minlength 1
         :maxlength qs/max-answer-length
         :name "answer"
         :placeholder (tr [:search])
         :role "combobox"
         :type "text"}
        (:input opts))]
      ; A real button, so that Enter and Space activate it without a keydown handler.
      [:button.reveal-options
       {:type "button"
        :aria-label (tr [:show-all-options])
        :data-on:click reveal}
       (svg "arrow_drop_down.svg")]
      (when (seq matches)
        [:ul.autocomplete-list#autocomplete-list
         {:data-show "!$_closed"
          :role "listbox"}
         (for [[index {:keys [description]
                       :as option}] (map-indexed vector matches)
               :let [label (option-label option)]]
           [:li
            {:data-attr:aria-selected (format "String($_active == %d)" index)
             :data-on:click pick
             :data-option label
             :id (str "option-" index)
             :role "option"}
            label
            (when description
              [:span.description description])])])])))

(defmulti answers-view
  (fn [& args]
    (-> args
        last
        :type)))

(defmethod answers-view :multiple
  [tr
   ^Boolean disabled?
   {:keys [answer-revealed?]
    :as answers}
   ^String game-id
   ^Boolean mark-correct?
   {:keys [choices note]}]
  [:form#answers
   [:ul#choices
    (answer-form-handler disabled? game-id)
    (for [{:keys [correct? index text]} (->> choices add-index util/deterministic-shuffle)]
      [:label.btn
       {:class (when mark-correct?
                 (if correct? "correct" "incorrect"))}
       [:input
        {:disabled (or disabled? answer-revealed?)
         :name "answer"
         :type "checkbox"
         :value index}]
       [:div.answer
        [:div
         text
         (mark-answer tr mark-correct? correct?)]
        (answer-frequency answers index)]])]
   (note-view answer-revealed? note)])

(defmethod answers-view :yesno
  [tr
   ^Boolean disabled?
   {:keys [answer-revealed?]
    :as answers}
   ^String game-id
   ^Boolean mark-correct?
   {:keys [correct? note]}]
  [:form#answers
   [:ul#choices
    (answer-form-handler disabled? game-id)
    (for [{:keys [answer correct? label]} [{:answer true
                                            :correct? correct?
                                            :label :question.yesno/yes}
                                           {:answer false
                                            :correct? (not correct?)
                                            :label :question.yesno/no}]]
     [:label.btn
      {:class (when mark-correct?
                (if correct? "correct" "incorrect"))}
      [:input
       {:disabled (or disabled? answer-revealed?)
        :name "answer"
        :type "checkbox"
        :value (str answer)}]
      [:div.answer
       [:div
        (tr [label])
        (mark-answer tr mark-correct? correct?)]
       (answer-frequency answers answer)]])]
   (note-view answer-revealed? note)])

(defmethod answers-view :percent-range
  [tr
   ^Boolean disabled?
   {:keys [answer-revealed?]}
   ^String game-id
   _
   {:keys [note percentage]}]
  [:div
   (when-not disabled?
     [:form#answers
      [:p.percent-picker
       {:data-signals "{_answer: 50}"}
       [:button.key
        {:data-on:click__prevent "$_answer--"}
        "-"]
       [:label.percentage
        {:data-text "$_answer + ' %'"
         :for "answer"}]
       [:button.key
        {:data-on:click__prevent "$_answer++"}
        "+"]]
      [:p.range-input
       [:input
        {:data-bind "_answer"
         :list "markers"
         :max "100"
         :min "0"
         :name "answer"
         :type "range"}]
       [:datalist#markers
        (for [value (->> 0
                         (iterate (partial + 25))
                         (take 5)
                         (map str))]
          [:option {:value value}])]]
      [:p (submit-button tr game-id)]])
   (when answer-revealed?
     (revealed-answer (format "%s %%" (util/decimal-format percentage))))
   (note-view answer-revealed? note)])

(defmethod answers-view :open
  [tr
   ^Boolean disabled?
   {:keys [answer-revealed?]
    :as answers}
   ^String game-id
   _
   {:keys [answer note]}]
  [:form#answers
   (when-not disabled?
     [:p
      [:input
       {:autofocus true
        :minlength 1
        :maxlength qs/max-answer-length
        :name "answer"
        :type "text"}]
      (submit-button tr game-id)])
   (when answer-revealed?
     (revealed-answer answer))
   (open-answers tr answers)
   (note-view answer-revealed? note)])

(defmethod answers-view :sort
  [tr
   ^Boolean disabled?
   {:keys [answer-revealed?]}
   ^String game-id
   ^Boolean mark-correct?
   {:keys [items note]}]
  (let [shuffled-items (->> items
                            add-index
                            util/deterministic-shuffle)]
    [:form#answers
     {:data-signals:_answer (->> shuffled-items
                                 (map :index)
                                 charred/write-json-str)
      :data-on:reordered "$_answer = evt.detail"}
     (if disabled?
       [:ul#sortableList
        {:class ["disabled" (when mark-correct? "revealed")]}
        (if mark-correct?
          (for [{:keys [sort-value text]} (sort-by :sort-value items)]
            [:li
             [:span text]
             [:span.sort-value sort-value]])
          (for [{:keys [text]} shuffled-items]
            [:li [:span text]]))]
       [:sortable-list
        {:items (->> shuffled-items
                     (map (fn [{:keys [index text]}] {:value index :label text}))
                     charred/write-json-str)
         ;; Morphing would empty what the component renders into itself.
         :data-ignore-morph ""}])
     [:input
      {:data-attr:value "$_answer"
       :name "answer"
       :type "hidden"}]
     (when-not disabled?
       [:p (submit-button tr game-id)])
     (note-view answer-revealed? note)]))

(defmethod answers-view :player-choice
  [_
   ^Boolean disabled?
   {:keys [answer-revealed?]
    :as answers}
   ^String game-id
   _
   {:keys [note]}]
  [:form#answers
   [:ul#choices
    (answer-form-handler disabled? game-id)
    (for [{:keys [index player-name]} (->> game-id game/game-players add-index util/deterministic-shuffle)]
      [:label.btn
       [:input
        {:disabled (or disabled? answer-revealed?)
         :name "answer"
         :type "checkbox"
         :value index}]
       [:div.answer
        [:div
         player-name]
        (answer-frequency answers index)]])]
   (note-view answer-revealed? note)])

(defmethod answers-view :autocomplete
  [tr
   ^Boolean disabled?
   {:keys [answer-revealed?]
    :as answers}
   ^String game-id
   _
   {:keys [choices note session-id]}]
  [:form#answers
   (when-not disabled?
     [:p (autocomplete tr game-id session-id choices)])
   (open-answers tr answers)
   (note-view answer-revealed? note)])

;; Showing a node focuses it on the map, and selects it if it is a choice, but leaves the
;; answer to the submit button, so browsing the suggestions cannot answer by accident.
(def ^:private show-node
  "document.querySelector('network-map').dispatchEvent(new CustomEvent('network-show', {detail: %s}))")

(defn- network-search
  "Search for a node of the network that `choices` describe, which shows the suggestion
  picked or highlighted on the map. Every node is searched, those that are not choices
  included, as they lead to the choices around them. Suggestions go without descriptions,
  which the map shows for the node it shows."
  [tr game-id session-id choices]
  (let [options (for [node (:nodes (network/network choices))]
                  {:label node})]
    (autocomplete tr game-id session-id options
                  {:pick  (str "$_closed = true; $_active = -1; $autocomplete = el.dataset.option; "
                               (format show-node "el.dataset.option"))
                   ; Not the answer, which the map gives, and no keyboard over the map.
                   :input {:autofocus false
                           :name nil
                           :data-effect (str "$_active >= 0 && "
                                             (format show-node "document.getElementById('option-' + $_active)?.dataset.option"))}})))

(defmethod answers-view :network
  [tr
   ^Boolean disabled?
   {:keys [answer-revealed?]
    :as answers}
   ^String game-id
   _
   {:keys [choices note session-id]}]
  (let [{:keys [answer-count answer-frequencies]} answers]
    [:form#answers
     (when-not disabled?
       [:div
        {:data-signals:_answer "''"
         ;; A node focused that is no choice clears the answer, and with it its
         ;; description. Either is named in the search box, unless the player is going
         ;; through its suggestions, whose own text that would replace.
         :data-on:network-select (str "$_answer = evt.detail.choice ? evt.detail.id : ''; "
                                      "$_active < 0 && ($autocomplete = evt.detail.id)")}
        (network-map tr choices)
        ;; Under the map, which is the way to find a node; the search is the shortcut.
        (network-search tr game-id session-id choices)
        ;; The description of the choice selected, under the box that names it.
        [:div.network-description
         (for [{:keys [label description]} choices
               :when description]
           [:div.description
            {:data-show (format "$_answer === %s" (charred/write-json-str label))
             :style "display: none"}
            description])]
        [:input
         {:data-attr:value "$_answer"
          :name "answer"
          :type "hidden"}]
        [:p (assoc-in (submit-button tr game-id) [1 :data-attr:disabled] "!$_answer")]])
     ;; The tree stands in for the ranked list every other crowd-scored type gets: this is
     ;; the one question type where how far apart the answers were is the result, which is
     ;; also what the scoring measures, and a list of labels cannot show it.
     (when (and answer-revealed? (pos? (or answer-count 0)))
       (result-tree choices answer-frequencies answer-count))
     (note-view answer-revealed? note)]))
