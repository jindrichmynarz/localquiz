(ns net.mynarz.localquiz.middleware
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.session :as session]
            [net.mynarz.localquiz.util :refer [read-json]]
            [clojure.math :as math]
            [reitit.ring.middleware.multipart :as multipart]
            [ring.middleware.reload :as reload]
            [starfederation.datastar.clojure.consts :as consts]))

(defn reloading-ring-handler
  "Reload Ring handler on each request."
  [f]
  (let [reload! (#'reload/reloader ["src"] true)]
    (fn
      ([request]
       (reload!)
       ((f) request))
      ([request respond raise]
       (reload!)
       ((f) request respond raise)))))

(defn wrap-blocker
  [handler]
  (fn [request]
    (cond
      ;; If you don't support Brotli (bots), you get nothing.
      (not (some->> ((:headers request) "accept-encoding")
                    (re-find #"(?:^| )br(?:$|,)")))
      {:status 406}

      :else (handler request))))

(defn wrap-language
  "Ring middleware adding the $language signal for Tempura."
  [handler]
  (fn [{{:keys [language]} :signals
        :as request}]
    (-> request
       (cond-> language (assoc :tempura/locales [(keyword language)]))
       handler)))

(def wrap-multipart
  "Allows uploading 1 file up to 1 MB in size."
  (multipart/create-multipart-middleware {:max-file-size (math/pow 10 6)})) ; 1 MB

(defn wrap-parse-signals
  "Ring middleware parsing Datastar signals in JSON."
  [handler]
  (fn [{:keys [body content-type request-method]
        {signals consts/datastar-key} :query-params
        :as request}]
    (handler
      (cond-> request
        (and (= request-method :post) (= content-type "application/json") body)
        (assoc :signals (read-json body))

        (and (= request-method :get) signals)
        (assoc :signals (read-json signals))))))

(defn wrap-session
  "Ring middleware wrapping sessions"
  [handler]
  (let [csrf-keyspec (crypto/secret-key->hmac-sha256-keyspec (:csrf-secret config))
        sid->csrf (fn [sid] (crypto/hmac-md5 csrf-keyspec sid))]
    (fn [{:keys [headers request-method signals]
          :as request}]
      (let [csrf (or (get headers "x-csrf-token") (:csrf signals))
            sid (session/get-sid headers)]
        (cond
          ; If user has a sid and csrf, handle the request.
          (and sid (= csrf (sid->csrf sid)))
          (-> request
              (assoc :sid sid)
              handler)

          ; GET request and user does not have session we create one
          ; if they do not have a csrf cookie we give them one
          (= request-method :get)
          (let [new-sid (or sid (crypto/random-unguessable-uid))
                new-csrf (sid->csrf new-sid)]
            (-> request
                (assoc :sid new-sid)
                handler
                (assoc-in [:headers "Set-Cookie"]
                          ; These cookies won't be set on local host on chrome/safari
                          ; as it's using secure needs to be true and local host
                          ; does not have HTTPS. SameSite is set to lax as it
                          ; allows the same cookie session to be used following a
                          ; link from another site.
                          [(session/session-cookie new-sid)
                           (session/csrf-cookie new-csrf)])))

          ; Non-GET requests without a session get HTTP 403 Forbidden.
          ; Note: If the updates SSE connection is a not a :get then this
          ; will close the connection until the user reloads the page.
          :else
          {:status 403})))))
