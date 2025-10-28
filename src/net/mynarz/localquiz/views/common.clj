(ns net.mynarz.localquiz.views.common
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.headers :refer [default-headers]]
            [net.mynarz.localquiz.session :as session]
            [dev.onionpancakes.chassis.compiler :as cc]
            [dev.onionpancakes.chassis.core :as h]
            [starfederation.datastar.clojure.brotli :as brotli]))

(def on-load-js
  ;; Quirk with browsers is that cache settings are per URL not per
  ;; URL + METHOD this means that GET and POST cache headers can
  ;; mess with each other. To get around this an unused query param
  ;; is added to the url.

  ;; Retry Infinity means we always try to reconnect. The other defaults
  ;; mean that this will at most take 30s (default max backoff).
  "@post(window.location.pathname + (window.location.search + '&u=').replace(/^&/,'?'), {retryMaxCount: Infinity})")

(def shim-page
  "A basic HTML page with Datastar setup."
  [h/doctype-html5
   [:html {:lang "en"}
    [:head
     [:title "Localquiz"]
     [:meta {:charset "UTF-8"}]
     [:script#js {:defer true
                  :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.5/bundles/datastar.js"
                  :type "module"}]
     ; Enables responsiveness on mobile devices
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1.0"}]]
    [:body {:data-signals-csrf  session/csrf-cookie-js
            :data-on-load on-load-js
            ;; Reconnect when the user comes online after
            ;; being offline. Closes any existing connection
            ;; from this div.
            :data-on-online__window on-load-js}
     [:noscript "Your browser does not support JavaScript!"]
     [:main#morph]]]])

(def shim-view
  (let [body (-> shim-page
                 cc/compile
                 h/html)]
    {:status 200
     :headers (merge default-headers
                     {"Content-Encoding" "br"
                      "ETag" (crypto/digest body)})
     :body (brotli/compress body :quality 11)}))
