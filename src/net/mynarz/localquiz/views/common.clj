(ns net.mynarz.localquiz.views.common
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.headers :as headers]
            [net.mynarz.localquiz.session :as session]
            [net.mynarz.localquiz.util :as util :refer [svg]]
            [charred.api :as charred]
            [dev.onionpancakes.chassis.compiler :as cc]
            [dev.onionpancakes.chassis.core :as h]
            [starfederation.datastar.clojure.api :refer [CDN-url]]
            [starfederation.datastar.clojure.brotli :as brotli]))

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
    :data-on:change "$language = {'cs': 'en', 'en': 'cs'}[$language];
                     localStorage.setItem('language', $language);
                     location.reload()"
    :type "checkbox"}])

(defn lang-switch
  [tr]
  [:p#lang-switch
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
       [:abbr {:title (tr [:footer/persistence])} "🧡"]
       (tr [:footer/using])
       [:a {:href "https://clojure.org"} "Clojure"]
       (tr [:and])
       [:a {:href "https://data-star.dev"} "Datastar"]
       "🚀."])]])

(defn cookie-warning
  [tr]
  [:div#cookie-warning
   {:aria-live "polite"
    :data-signals:_cookie-accepted "localStorage.getItem('cookie-accepted') || false"
    :data-show "!$_cookieAccepted"
    :role "dialog"}
   [:p (tr [:cookie-warning])]
   [:div.buttons
    [:button.btn.btn-primary
     {:data-on:click "($_cookieAccepted = true) && localStorage.setItem('cookie-accepted', 'true')"}
     (tr [:accept])]]])

(defn error-dialog
  [tr]
  [:div#error-dialog
   {:aria-live "assertive"
    :data-on:keydown__window "evt.key === 'Escape' && ($error = '')"
    :data-show "$error"
    :role "dialog"}
   [:h2 (tr [:errors/errors])]
   [:pre {:data-text "$error"}]
   [:button.btn.btn-primary
    {:data-on:click "$error = ''"}
    (tr [:close])]])

(defn morph-body
  ([request]
   (morph-body request nil nil nil))
  ([request main]
   (morph-body request nil nil main))
  ([request header main]
   (morph-body request nil header main))
  ([{:tempura/keys [tr]} center header main]
   [:div#morph
    {:data-signals:error__ifmissing ""}
    [:header
     [:h1 "Localquiz"]
     [:div#header-center center]
     [:div#menu-trigger
      {:data-signals:_menu-open "false"}
      [:div#top-menu
       {:data-class:open "$_menuOpen"}
       header
       (lang-switch tr)]
      [:i.material-icons
       {:data-on:click "$_menuOpen = !$_menuOpen"}
       (svg "menu.svg")]]]
    [:main main]
    (footer tr)
    (cookie-warning tr)
    (error-dialog tr)]))

(defn shim-page
  "A basic HTML page with Datastar setup."
  [{{:keys [game-id]} :path-params
    :tempura/keys [tr]
    :as request}]
  [h/doctype-html5
   [:html
    {:data-attr:lang "$language"}
    [:head
     [:title "Localquiz"]
     [:meta
      {:charset "UTF-8"}]
     [:link#css
      {:href "/css/style.css"
       :rel "stylesheet"
       :type "text/css"}]
     [:link
      {:as "script"
       :href "/js/sortable.js"
       :rel "modulepreload"}]
     [:link
      {:as "script"
       :href "/js/quiz.js"
       :rel "modulepreload"}]
     [:script
      {:defer true
       :src CDN-url
       :type "module"}]
     [:script
      {:src "/js/sortable.js"
       :type "module"}]
     [:script
      {:src "/js/quiz.js"
       :type "module"}]
     ; Enables responsiveness on mobile devices
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1.0"}]]
    [:body {:data-signals:csrf session/csrf-cookie-js
            :data-signals:language "localStorage.getItem('language') || navigator.language.slice(0, 2)"
            :data-init (init-js game-id)
            ; Reconnect when the user comes online after
            ; being offline. Closes any existing connection
            ; from this element.
            :data-on:online__window (init-js game-id)}
     [:noscript (tr [:no-js])]
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
        :body (brotli/compress body :quality 11)})
    ; No content
    {:headers {"Strict-Transport-Security" headers/strict-transport
               "Cache-Control" "no-store"}
     :status 204}))

(def shim-view
  (comp view shim-page))

(defn ->session-role
  "Get the session role based on path parameters.
  Player paths always contain the `game-id`."
  [{{:keys [game-id]} :path-params}]
  (if game-id :player :moderator))

(defmulti game-view
  (juxt ->session-role (comp :state :game)))

(defn morph-view
  [{{:keys [game-id]} :path-params
    session-id :sid
    :as request}]
  (->> {:session-role (if game-id :player :moderator)
        :state (game/get-game-state (or game-id session-id))}
       (assoc request :game)
       game-view))

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
  [:button.btn#submit
   {:data-on:click (answer-handler game-id)}
   (tr [:submit])])

(defn mark-answer
  [tr
   ^Boolean answer-revealed?
   ^Boolean correct?]
  (when (and answer-revealed? correct?)
    [:i.material-icons
     {:aria-hidden "true"
      :aria-label (tr [:correct])}
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

(defn answer-frequency
  [{:keys [answer-count
           answer-frequencies
           answer-revealed?]}
   answer]
  (when-let [frequency (and answer-revealed? (get answer-frequencies answer))]
    [:progress.answer-frequency
     {:max answer-count
      :value frequency}]))

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
         [:td
          [:progress.answer-frequency
           {:max answer-count
            :value frequency}]]])]]))

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
      [:p
       {:data-signals "{_answer: 50}"}
       [:button.btn
        {:data-on:click__prevent "$_answer--"}
        "-"]
       [:label.percentage
        {:data-text "$_answer + ' %'"
         :for "answer"}]
       [:button.btn
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
     [:p (format "%s %%" (util/decimal-format percentage))])
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
        :maxlength 100
        :name "answer"
        :type "text"}]
      (submit-button tr game-id)])
   (when answer-revealed?
     [:div.answer.revealed
      [:p answer [:i.material-icons (svg "check.svg")]]])
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
                            util/deterministic-shuffle)
        drag-indicator [:i.material-icons (svg "drag_indicator.svg")]]
    [:form#answers
     [:ul#sortableList
      {:class (when disabled? "disabled")
       :data-init (when-not disabled? "createSortableList(el)")
       :data-signals:_answer (->> shuffled-items
                                  (map :index)
                                  charred/write-json-str)
       :data-on:reordered "$_answer = evt.detail"}
      (if mark-correct?
        (for [{:keys [sort-value text]} (sort-by :sort-value items)]
          [:li
           [:span text]
           [:span.sort-value sort-value]])
        (for [{:keys [index text]} shuffled-items]
          [:li
           {:data-index index}
           [:span text]
           (when-not disabled?
             drag-indicator)]))
      [:input
       {:data-attr:value "$_answer"
        :name "answer"
        :type "hidden"}]]
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
