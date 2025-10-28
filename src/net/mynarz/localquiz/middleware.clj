(ns net.mynarz.localquiz.middleware
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.session :as session]
            [net.mynarz.localquiz.util :refer [read-json]]
            [ring.middleware.reload :as reload]
            [taoensso.timbre :as log]))

(defn parse-json-body?
  "Detemine if request's body should be parsed as JSON."
  [{:keys [body content-type request-method]}]
  (and (= request-method :post)
       (= content-type "application/json")
       body))

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
      ;; If you don't support Brotli you get nothing (bots).
      (not (some->> ((:headers request) "accept-encoding")
                    (re-find #"(?:^| )br(?:$|,)")))
      {:status 406}

      :else (handler request))))

(defn wrap-parse-json-body
  "Ring middleware parsing request bodies in JSON."
  [handler]
  (fn [request]
    (cond-> request
      (parse-json-body? request) (update :body read-json)
      true                       handler)))

(defn wrap-session
  "Ring middleware wrapping sessions"
  [handler]
  (let [csrf-keyspec (crypto/secret-key->hmac-sha256-keyspec (:csrf-secret config))
        sid->csrf (fn [sid] (crypto/hmac-md5 csrf-keyspec sid))]
    (fn [{{:keys [csrf]} :body
          :keys [headers request-method]
          :as request}]
      (let [sid (session/get-sid headers)]
        (cond
          ; If user has a sid and csrf, handle the request.
          (and sid (= csrf (sid->csrf sid)))
          (handler (assoc request
                          :sid sid
                          :csrf csrf))

          ; :get request and user does not have session we create one
          ; if they do not have a csrf cookie we give them one
          (= request-method :get)
          (let [new-sid (or sid (crypto/random-unguessable-uid))
                new-csrf (sid->csrf new-sid)]
            (-> request
                (assoc :sid new-sid
                       :csrf new-csrf)
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
