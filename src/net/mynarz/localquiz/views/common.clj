(ns net.mynarz.localquiz.views.common
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.headers :as headers]
            [net.mynarz.localquiz.session :as session]
            [net.mynarz.localquiz.util :refer [decimal-format long-str]]
            [dev.onionpancakes.chassis.compiler :as cc]
            [dev.onionpancakes.chassis.core :as h]
            [starfederation.datastar.clojure.brotli :as brotli]
            [starfederation.datastar.clojure.api :refer [CDN-url]]
            [taoensso.timbre :as log])
  (:import (java.util Date)))

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
      [:script#js {:defer true
                   :src CDN-url
                   :type "module"}]
      ; Enables responsiveness on mobile devices
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1.0"}]]
     [:body {:data-signals:csrf  session/csrf-cookie-js
             :data-init on-load-js
             ;; Reconnect when the user comes online after
             ;; being offline. Closes any existing connection
             ;; from this div.
             :data-on:online__window on-load-js}
      [:noscript "Your browser does not support JavaScript!"]
      cookie-warning
      [:main
       [:h1 [:a {:href "/"} "Localquiz"]]
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

(defn patch-view
  [{{:keys [game-id]} :path-params
    :as request}]
  (let [game-session (game/get-session game-id)]
    [:div#morph
     (game-view (assoc request :game game-session))]))

(defn- answer-click-handler
  [^Boolean disabled?
   ^Boolean signal?
   ^String game-id]
  (when-not disabled?
    {:data-on:click (long-str "evt.target.tagName = 'BUTTON'"
                              (when-not signal? "&& ($answer = evt.target.dataset.answer)")
                              (format "&& @post('/answer/%s')" game-id))}))

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
  [^Boolean answer-revealed?
   ^Date question-added]
  (when-not answer-revealed?
    (let [duration (:question-time-out config)
          signals (format "{_timer: {start: new Date(%d), delay: 0}}" (.getTime question-added))
          sync-animation (format "($_timer.delay = - ((Date.now() - $_timer.start) / 1000) %% %d)" duration)]
      [:div.timer
       {:data-signals signals
        :data-on:visibilitychange__window (str "!document.hidden && " sync-animation)
        :data-style:animationDelay "$_timer.delay"
        :data-style:--duration (format "'%ds'" duration)}
       [:div]])))

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
   {:keys [question-added]
    {:keys [choices note]} :current-question}]
  [:section#answers
   (timer answer-revealed? question-added)
   [:ul#choices
    (answer-click-handler (or disabled? answer-revealed?) false game-id)
    (map-indexed
      (fn [index {:keys [correct? text]}]
        [:li
         [:button.btn
          {:class (when answer-revealed?
                    (if correct? "correct" "incorrect"))
           :data-answer index
           :disabled (or disabled? answer-revealed?)}
          [:span.answer
            text
           (mark-answer answer-revealed? correct?)]]])
      choices)]
   (note-view answer-revealed? note)])

(defmethod answers-view :yesno
  [^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [question-added]
    {:keys [correct? note]} :current-question}]
  [:section#answers
   (timer answer-revealed? question-added)
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
   {:keys [question-added]
    {:keys [note percentage]} :current-question}]
  [:section#answers
   (timer answer-revealed? question-added)
   (when-not disabled?
     [:div
      [:p
       (answer-click-handler answer-revealed? true game-id)
       [:input
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
          [:option {:value value}])]
       [:span.percentage
        {:data-text "$answer + ' %'"}]]
      [:p [:button.btn#submit "Submit"]]])
   (when answer-revealed?
     [:p (format "%s %%" (decimal-format percentage))])
   (note-view answer-revealed? note)])

(defmethod answers-view :open
  [^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [question-added]
    {:keys [answer note]} :current-question}]
  [:section#answers
   (timer answer-revealed? question-added)
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
     [:p answer])
   (note-view answer-revealed? note)])

(defmethod answers-view :sort
  [^Boolean disabled?
   ^Boolean answer-revealed?
   ^String game-id
   {:keys [question-added]
    {:keys [items note]} :current-question}]
  [:section#answers
   (answer-click-handler answer-revealed? true game-id)
   (timer answer-revealed? question-added)
   [:ul.sortable-list
    ; TODO: Is `data-computed` recalculated when DOM changes?
    ;       Hook it to a custom event from Sortable.js like in <https://data-star.dev/examples/sortable>.
    {:data-computed:answer "[...el.querySelectorAll('li')].map(el => el.dataset.index)"}
    (map-indexed
      (fn [index {:keys [sort-value text]}]
        [:li
         {:data-index index}
         [:div text]
         (when answer-revealed?
           [:div.sort-value sort-value])])
      items)
    (note-view answer-revealed? note)]])
