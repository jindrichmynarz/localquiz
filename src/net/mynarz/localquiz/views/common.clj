(ns net.mynarz.localquiz.views.common
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.headers :as headers]
            [net.mynarz.localquiz.session :as session]
            [net.mynarz.localquiz.util :refer [decimal-format long-str]]
            [charred.api :as charred]
            [dev.onionpancakes.chassis.compiler :as cc]
            [dev.onionpancakes.chassis.core :as h]
            [starfederation.datastar.clojure.brotli :as brotli]
            [starfederation.datastar.clojure.api :refer [CDN-url]]
            [taoensso.timbre :as log]
            [net.mynarz.localquiz.game :as game]))

; Warn on ambiguous attributes
(cc/set-warn-on-ambig-attrs!)

(def on-load-js
  ;; Quirk with browsers is that cache settings are per URL not per
  ;; URL + METHOD this means that GET and POST cache headers can
  ;; mess with each other. To get around this an unused query param
  ;; is added to the url.

  ;; Retry Infinity means we always try to reconnect. The other defaults
  ;; mean that this will at most take 30s (default max backoff).
  "@post(window.location.pathname + (window.location.search + '&u=').replace(/^&/,'?'), {retryMaxCount: Infinity})")

(def submit-by-enter
   "evt.key === 'Enter' && document.getElementById('submit').click()")

(defn lang-switch-input
  []
  [:input.offscreen#lang-toggle
    {:data-attr:checked "$language == 'en'"
     :data-on:change "localStorage.setItem('language', {'cs': 'en', 'en': 'cs'}[$language]); location.reload()"
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
  [tr]
  [:footer
   [:p
    (interpose
      " "
      [(tr [:footer/made-with])
       [:abbr {:title (tr [:footer/persistence])} "🧡"]
       (tr [:footer/using])
       [:a {:href "https://clojure.org"} "Clojure"]
       (tr [:and])
       [:a {:href "https://data-star.dev"} "Datastar"]
       "🚀."])]])

(def cookie-warning
  [:div#cookie-warning
   {:aria-live "polite"
    :data-signals:_cookie-accepted "localStorage.getItem('cookie-accepted') || false"
    :data-show "!$_cookieAccepted"
    :role "dialog"}
   [:p "Localquiz uses cookies for its functionality."]
   [:div.buttons
    [:button.btn.btn-primary
     {:data-on:click "($_cookieAccepted = true) && localStorage.setItem('cookie-accepted', 'true')"}
     "Accept"]
    [:button.btn
     {:data-on:click "window.close()"}
     "Exit"]]])

(defn shim-page
  "A basic HTML page with Datastar setup."
  [{:keys [tr]}]
  [h/doctype-html5
   [:html
    {:data-attr:lang "$language"}
    [:head
     [:title "Localquiz"]
     [:meta
      {:charset "UTF-8"}]
     [:link#css
      {:rel "stylesheet"
       :type "text/css"
       :href "/css/style.css"}]
     [:link
      {:href "https://fonts.googleapis.com/icon?family=Material+Icons"
       :rel "stylesheet"}]
     [:script {:defer true
               :src CDN-url
               :type "module"}]
     ; Enables responsiveness on mobile devices
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1.0"}]]
    [:body {:data-signals:csrf session/csrf-cookie-js
            :data-signals:language "localStorage.getItem('language') || navigator.language.slice(0, 2)"
            :data-init on-load-js
            ;; Reconnect when the user comes online after
            ;; being offline. Closes any existing connection
            ;; from this div.
            :data-on:online__window on-load-js}
     [:noscript (tr [:no-js])]
     [:div#screen
      [:header
       [:h1 "Localquiz"]
       (lang-switch tr)]
      [:main#morph]
      (footer tr)
      cookie-warning]]]])

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

(defn answer-click-handler
  [^Boolean disabled?
   ^Boolean signal?
   ^String game-id]
  (when-not disabled?
    ; FIXME: Still doesn't work reliably for :multiple questions.
    ;        Submit answers as [form data](https://data-star.dev/examples/form_data) instead of signals?
    ;        Or submit via a GET query parameter?
    (let [post (format "@post('/answer/%s')" game-id)
          signal-and-post (format "($answer = evt.target.dataset.answer, %s)" post)]
      {:data-on:click (long-str "evt.target.tagName = 'BUTTON' &&"
                                (if signal? post signal-and-post))})))

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

(defn submit-button
  [tr
   ^String game-id]
  [:button.btn#submit
   {:data-on:click (format "@post('/answer/%s')" game-id)}
   (tr [:submit])])

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
  [:section#answers
   [:ul#choices
    (answer-click-handler (or disabled? answer-revealed?) false game-id)
    (for [{:keys [correct? index text]} (->> choices add-index crypto/deterministic-shuffle)]
      [:li
       [:button.btn
        {:class (when answer-revealed?
                  (if correct? "correct" "incorrect"))
         :data-answer index
         :disabled (or disabled? answer-revealed?)}
        [:span.answer
          text
         (mark-answer answer-revealed? correct?)]]])]
   (note-view answer-revealed? note)])

(defmethod answers-view :yesno
  [tr
   ^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [correct? note]}]
  [:section#answers
   [:p
    (answer-click-handler (or disabled? answer-revealed?) false game-id)
    [:button.btn
     {:class (when answer-revealed?
               (if correct? "correct" "incorrect"))
      :data-answer "true"
      :disabled disabled?}
     (tr [:question.yesno/yes])
     (mark-answer answer-revealed? correct?)]
    [:button.btn
     {:class (when answer-revealed?
               (if-not correct? "correct" "incorrect"))
      :data-answer "false"
      :disabled disabled?}
     (tr [:question.yesno/no])
     (mark-answer answer-revealed? (not correct?))]]
   (note-view answer-revealed? note)])

(defmethod answers-view :percent-range
  [tr
   ^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [note percentage]}]
  [:section#answers
   (when-not disabled?
     [:div
      [:p.range-input
       [:input#answer
        {:data-bind "answer"
         :list "markers"
         :max "100"
         :min "0"
         :type "range"
         :value "50"}]
       [:datalist#markers
        (for [value (->> 0
                         (iterate (partial + 25))
                         (take 5)
                         (map str))]
          [:option {:value value}])]]
      [:p
       [:button.btn
        {:data-on:click "$answer--"}
        "-"]
       [:label.percentage
        {:data-text "$answer + ' %'"
         :for "answer"}]
       [:button.btn
        {:data-on:click "$answer++"}
        "+"]]
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
  [:section#answers
   (when-not disabled?
     [:p
      [:input
       {:autofocus true
        :data-bind "answer"
        :data-on:keydown submit-by-enter
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
  [:section#answers
   [:ul#sortableList
    {:class (when disabled? "disabled")
     :data-signals:answer (->> items
                               count
                               range
                               charred/write-json-str)
     :data-on:reordered "$answer = evt.detail"}
    (if answer-revealed?
      (for [{:keys [sort-value text]} items]
        [:li
         [:span text]
         [:span.sort-value sort-value]])
      (for [{:keys [index text]} (->> items add-index crypto/deterministic-shuffle)]
        [:li
         {:data-index index}
         [:span text]]))
    (when-not disabled?
      [:p
       [:script {:src "/js/sortable.js"
                 :type "module"}]
       (submit-button tr game-id)])
    (note-view answer-revealed? note)]])
