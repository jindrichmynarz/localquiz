(ns net.mynarz.localquiz.views.common
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.headers :as headers]
            [net.mynarz.localquiz.session :as session]
            [dev.onionpancakes.chassis.compiler :as cc]
            [dev.onionpancakes.chassis.core :as h]
            [starfederation.datastar.clojure.brotli :as brotli]
            [taoensso.timbre :as log]))

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

(def cookie-warning
  [:div#cookie-warning
   {:aria-live "polite"
    :data-signals:_cookie-accepted "localStorage.getItem('cookie-accepted') || false"
    :data-show "!$_cookieAccepted"
    :role "dialog"}
   [:p "Localquiz uses cookies for its functionality."]
   [:div.buttons
    [:button.btn-primary
     {:data-on:click "($_cookieAccepted = true) && localStorage.setItem('cookie-accepted', 'true')"}
     "Accept"]
    [:button
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
      [:link#css {:rel "stylesheet"
                  :type "text/css"
                  :href "/css/style.css"}]
      [:script#js {:defer true
                   :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js"
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
     [:h1 "Localquiz"]
     (game-view (assoc request :game game-session))]))
