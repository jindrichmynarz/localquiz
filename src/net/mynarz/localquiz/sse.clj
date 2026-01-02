(ns net.mynarz.localquiz.sse
  (:require [net.mynarz.localquiz.async :refer [refresh-pub throttle]]
            [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.cpu-pool :refer [on-cpu-pool]]
            [net.mynarz.localquiz.error :as error]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.util :refer [thread]]
            [clojure.core.async :as a]
            [dev.onionpancakes.chassis.core :as h]
            [starfederation.datastar.clojure.adapter.http-kit :as hk-gen]
            [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.brotli :as brotli]
            [taoensso.timbre :as log]))

(defn handler
  "Server-sent events handler that runs `render-fn` for each game update."
  [render-fn
   {{last-event-id "last-event-id"} :headers
    {player-game-id :game-id} :path-params
    session-id :sid
    :as request}]
  (let [game-id (or player-game-id session-id)
        <ch (a/sub refresh-pub game-id (a/chan (a/dropping-buffer 1)))
        throttled<ch (throttle (:max-refresh-ms config) <ch)
        ; Poison pill for work cancelling
        <cancel (a/chan)]
    (hk-gen/->sse-response request
                           {hk-gen/write-profile (brotli/->brotli-profile)

                            hk-gen/on-open
                            (fn [sse-gen]
                              (log/infof "Opening a connection to game %s." game-id)
                              ; Ensures at least one render on connect
                              (a/>!! <ch :first-render)
                              (thread
                                (loop [last-view-hash last-event-id]
                                  (a/alt!!
                                    [<cancel]
                                    (doseq [ch [throttled<ch <ch <cancel]]
                                      (a/close! ch))

                                    [throttled<ch]
                                    ([_]
                                     (some-> ; Stop in case of error
                                      (on-cpu-pool ; CPU work on real threads
                                       ; Stop in case of error
                                       (when-some [new-view (error/try-on-error (render-fn request))]
                                         (let [new-view-str (h/html new-view)
                                               ; This is a very fast hash
                                               new-view-hash (Integer/toHexString (hash new-view-str))]
                                           ; Only send an event if the view has changed
                                           (when-not (= last-view-hash new-view-hash)
                                             (log/infof "Rendering game %s for session %s." game-id session-id)
                                             (d*/patch-elements! sse-gen
                                                                 new-view-str
                                                                 {:use-view-transition true}))
                                           new-view-hash)))
                                      recur))

                                    ; We want work cancelling to have higher priority.
                                    :priority true))))

                            hk-gen/on-close
                            (fn [sse-gen status]
                              (log/infof "Closing the session %s to game %s with status %s."
                                         session-id
                                         game-id
                                         status)
                              (a/>!! <cancel :cancel)
                              (when (and (not (:is-dev? config)) ; Don't close connections in development to allow testing.
                                         player-game-id
                                         (= (game/get-game-state player-game-id) :new))
                                (game/disconnect-player! session-id))
                              (d*/close-sse! sse-gen))})))
