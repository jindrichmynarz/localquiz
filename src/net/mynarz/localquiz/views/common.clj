(ns net.mynarz.localquiz.views.common
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.headers :as headers]
            [net.mynarz.localquiz.session :as session]
            [net.mynarz.localquiz.util :refer [decimal-format long-str]]
            [charred.api :as charred]
            [dev.onionpancakes.chassis.compiler :as cc]
            [dev.onionpancakes.chassis.core :as h]
            [starfederation.datastar.clojure.api :refer [CDN-url]]
            [starfederation.datastar.clojure.brotli :as brotli]
            [taoensso.timbre :as log]))

; Warn on ambiguous attributes
(cc/set-warn-on-ambig-attrs!)

(defn on-load-js
  ;; Retry Infinity means we always try to reconnect. The other defaults
  ;; mean that this will at most take 30s (default max backoff).
  [^String game-id]
  (let [endpoint (cond-> "/sse"
                    game-id (str "/" game-id))]
    (format "@get('%s', {retryMaxCount: Infinity})" endpoint)))

(def submit-by-enter
   "evt.key === 'Enter' && document.getElementById('submit').click()")

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

(defn footer
  ; FIXME: This is not translated to Czech.
  ;        Because it is outside of #morph?
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
     (tr [:accept])]
    [:button.btn
     {:data-on:click "window.close()"}
     (tr [:exit-game])]]])

(def material-icons
  "https://fonts.googleapis.com/icon?family=Material+Icons")

(defn shim-page
  "A basic HTML page with Datastar setup."
  [{{:keys [game-id]} :path-params
    :keys [tr]}]
  [h/doctype-html5
   [:html
    {:data-attr:lang "$language"}
    [:head
     [:title "Localquiz"]
     [:meta
      {:charset "UTF-8"}]
     [:link
      {:crossorigin true
       :href "https://fonts.gstatic.com"
       :rel "preconnect"}]
     [:link
      {:as "style"
       :href material-icons
       :rel "preload"}]
     [:link
      {:href material-icons
       :rel "stylesheet"}]
     [:link#css
      {:rel "stylesheet"
       :type "text/css"
       :href "/css/style.css"}]
     [:script
      {:defer true
       :src CDN-url
       :type "module"}]
     ; Enables responsiveness on mobile devices
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1.0"}]]
    [:body {:data-signals:csrf session/csrf-cookie-js
            :data-signals:language "localStorage.getItem('language') || navigator.language.slice(0, 2)"
            :data-init (on-load-js game-id)
            ;; Reconnect when the user comes online after
            ;; being offline. Closes any existing connection
            ;; from this div.
            :data-on:online__window (on-load-js game-id)}
     [:noscript (tr [:no-js])]
     [:div#screen
      [:header
       [:h1 "Localquiz"]
       (lang-switch tr)]
      [:main#morph]
      (footer tr)
      (cookie-warning tr)]]]])

(defn view
  [handler request]
  (let [response (handler request)]
    (if (some? response)
      (let [body (h/html response)]
        {:status 200
         :headers (merge headers/default-headers
                         {"Content-Encoding" "br"
                          "ETag" (crypto/digest body)})
         :body (brotli/compress body :quality 11)})
      {:headers {"Strict-Transport-Security" headers/strict-transport
                 "Cache-Control" "no-store"}
       :status 204})))

(def shim-view
  (partial view shim-page))

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
       game-view
       (vector :main#morph)))

(defn answer-handler
  [^String game-id]
  (format "@post('/answer/%s', {contentType: 'form', headers: {'X-Csrf-Token': $csrf}})" game-id))

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

(defn- mark-answer
  [^Boolean answer-revealed?
   ^Boolean correct?]
  (when (and answer-revealed? correct?)
    [:i.material-icons "check"]))

(defn- note-view
  [^Boolean answer-revealed?
   note]
  (when (and answer-revealed? note)
    [:div.note
     [:div [:i.material-icons.md-light.md-24 "info"]]
     [:div note]]))

(defn add-index
  [coll]
  (map-indexed (fn [index item] (assoc item :index index)) coll))

(defmulti answers-view
  (fn [& args]
    (-> args
        last
        :type)))

(defmethod answers-view :multiple
  [_
   ^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [choices note]}]
  [:form#answers
   [:ul#choices
    (answer-form-handler disabled? game-id)
    (for [{:keys [correct? index text]} (->> choices add-index crypto/deterministic-shuffle)]
      [:label.btn
       {:class (when answer-revealed?
                 (if correct? "correct" "incorrect"))}
       [:input
        {:disabled (or disabled? answer-revealed?)
         :name "answer"
         :type "checkbox"
         :value index}]
       [:span.answer
        text
        (mark-answer answer-revealed? correct?)]])]
   (note-view answer-revealed? note)])

(defmethod answers-view :yesno
  [tr
   ^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [correct? note]}]
  [:form#answers
   [:p#choices
    (answer-form-handler disabled? game-id)
    (for [{:keys [correct? label value]} [{:correct? correct?
                                           :label :question.yesno/yes
                                           :value "true"}
                                          {:correct? (not correct?)
                                           :label :question.yesno/no
                                           :value "false"}]]
     [:label.btn
      {:class (when answer-revealed?
                (if correct? "correct" "incorrect"))}
      [:input
       {:disabled (or disabled? answer-revealed?)
        :name "answer"
        :type "checkbox"
        :value value}]
      (tr [label])
      (mark-answer answer-revealed? correct?)])]
   (note-view answer-revealed? note)])

(defmethod answers-view :percent-range
  [tr
   ^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [note percentage]}]
  [:div
   (when-not disabled?
     [:form#answers
      [:p
       {:data-signals "{_answer: 50}"}
       [:button.btn
        {:data-on:click "$_answer-- && evt.preventDefault()"}
        "-"]
       [:label.percentage
        {:data-text "$_answer + ' %'"
         :for "answer"}]
       [:button.btn
        {:data-on:click "$_answer++ && evt.preventDefault()"}
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
      [:p
       (submit-button tr game-id)]])
   (when answer-revealed?
     [:p (format "%s %%" (decimal-format percentage))])
   (note-view answer-revealed? note)])

(defmethod answers-view :open
  [tr
   ^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [answer note]}]
  [:form#answers
   (when-not disabled?
     [:p
      [:input
       {:autofocus true
        :data-on:keydown submit-by-enter
        :name "answer"
        :type "text"}]
      (submit-button tr game-id)])
   (when answer-revealed?
     [:p.answer answer])
   (note-view answer-revealed? note)])

(defmethod answers-view :sort
  [tr
   ^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [items note]}]
  [:form#answers
   [:ul#sortableList
    {:class (when disabled? "disabled")
     :data-signals:_answer (->> items
                                count
                                range
                                charred/write-json-str)
     :data-on:reordered "$_answer = evt.detail"}
    (if answer-revealed?
      (for [{:keys [sort-value text]} items]
        [:li
         [:span text]
         [:span.sort-value sort-value]])
      (for [{:keys [index text]} (->> items add-index crypto/deterministic-shuffle)]
        [:li
         {:data-index index}
         [:span text]]))
    [:input
     {:data-attr:value "$_answer"
      :name "answer"
      :type "hidden"}]]
   (when-not disabled?
     [:p
      [:script
       {:src "/js/sortable.js"
        :type "module"}]
      (submit-button tr game-id)])
   (note-view answer-revealed? note)])
