(ns net.mynarz.localquiz.views.common
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.crypto :as crypto]
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

(def shim-page
  "A basic HTML page with Datastar setup."
  (cc/compile
   [h/doctype-html5
    [:html {:lang "en"}
     [:head
      [:title "Localquiz"]
      [:meta {:charset "UTF-8"}]
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
             :data-init on-load-js
             ;; Reconnect when the user comes online after
             ;; being offline. Closes any existing connection
             ;; from this div.
             :data-on:online__window on-load-js}
      [:noscript "Your browser does not support JavaScript!"]
      cookie-warning
      [:main
       [:h1 "Localquiz"]
       [:div#morph]
       [:footer
        [:p
         "Made with "
         [:abbr {:title "unreasonable persistence"} "🧡"]
         " using "
         [:a {:href "https://clojure.org"} "Clojure"]
         " and "
         [:a {:href "https://data-star.dev"} "Datastar"]
         " 🚀."]]]]]]))

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
  (partial view (constantly shim-page)))

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
       (vector :div#morph)))

(defn- answer-click-handler
  [^Boolean disabled?
   ^Boolean signal?
   ^String game-id]
  (when-not disabled?
    ; FIXME: Still doesn't work reliably for :multiple questions.
    {:data-on:click (long-str "evt.target.tagName = 'BUTTON' &&"
                              (when-not signal? "($answer = evt.target.dataset.answer) &&")
                              (format "@post('/answer/%s')" game-id))}))

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
     [:i.material-icons.md-light.md-24 "info"]
     note]))

(defn timer
  [^Boolean answer-revealed?]
  (when-not answer-revealed?
    (let [duration (:question-time-out config)
          sync-animation (format "($timer.delay = - ((Date.now() - Date($timer.start)) / 1000) %% %d)" duration)]
      [:div.timer
       {:data-on:visibilitychange__window (str "!document.hidden && " sync-animation)
        :data-style:animationDelay "$timer.delay"
        :data-style:--duration (format "'%ds'" duration)}
       [:div]])))

(defn add-index
  [coll]
  (map-indexed (fn [index item] (assoc item :index index)) coll))

(defmulti answers-view
  (fn [& args]
    (-> args
        last
        :current-question
        :type)))

(defmethod answers-view :multiple
  [^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {{:keys [choices note]} :current-question}]
  [:section#answers
   (timer answer-revealed?)
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
  [^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {{:keys [correct? note]} :current-question}]
  [:section#answers
   (timer answer-revealed?)
   [:p
     (answer-click-handler (or disabled? answer-revealed?) false game-id)
     [:button.btn
      {:class (when answer-revealed?
                (if correct? "correct" "incorrect"))
       :data-answer "true"
       :disabled disabled?}
      "Yes"
      (mark-answer answer-revealed? correct?)]
     [:button.btn
      {:class (when answer-revealed?
                (if-not correct? "correct" "incorrect"))
       :data-answer "false"
       :disabled disabled?}
      "No"
      (mark-answer answer-revealed? (not correct?))]]
   (note-view answer-revealed? note)])

(defmethod answers-view :percent-range
  [^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {{:keys [note percentage]} :current-question}]
  [:section#answers
   (timer answer-revealed?)
   (when-not disabled?
     [:div
      [:p.range-input
       (answer-click-handler answer-revealed? true game-id)
       [:input#answer
        ; FIXME: The $answer signal is not reset by the server.
        {:data-bind "answer" ; FIXME: $answer is initialized as false.
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
      [:p [:button.btn#submit "Submit"]]])
   (when answer-revealed?
     [:p (format "%s %%" (decimal-format percentage))])
   (note-view answer-revealed? note)])

(defmethod answers-view :open
  [^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {{:keys [answer note]} :current-question}]
  [:section#answers
   (timer answer-revealed?)
   (when-not disabled?
     [:p
      (answer-click-handler answer-revealed? true game-id)
      [:input
       {:autofocus true
        :data-bind "answer"
        :data-on:keydown submit-by-enter
        :type "text"}]
      [:button.btn#submit "Submit"]])
   (when answer-revealed?
     [:p.answer answer])
   (note-view answer-revealed? note)])

(defmethod answers-view :sort
  [^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {{:keys [items note]} :current-question}]
  [:section#answers
   (answer-click-handler answer-revealed? true game-id)
   (timer answer-revealed?)
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
      [:script {:src "/js/sortable.js"
                :type "module"}])
    (note-view answer-revealed? note)]])
