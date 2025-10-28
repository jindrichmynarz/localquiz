(ns net.mynarz.localquiz.sse
  (:require [net.mynarz.localquiz.async :refer [refresh-pub]]
            [net.mynarz.localquiz.cpu-pool :refer [on-cpu-pool]]
            [net.mynarz.localquiz.error :as error]
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
    moderator-game-id :sid
    :as request}]
  (let [game-id (or player-game-id moderator-game-id)
        <ch (a/sub refresh-pub game-id (a/chan (a/dropping-buffer 1)))
        ;; poison pill for work cancelling
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
                                    (do (a/close! <ch)
                                        (a/close! <cancel))

                                    [<ch]
                                    ([_]
                                     (log/infof "Rendering game %s." game-id)
                                     (some-> ; Stop in case of error
                                      (on-cpu-pool ; CPU work on real threads
                                       ; Stop in case of error
                                       (when-some [new-view (error/try-on-error (render-fn request))]
                                         (let [new-view-str (h/html new-view)
                                               ; This is a very fast hash
                                               new-view-hash (Integer/toHexString (hash new-view-str))]
                                           ; Only send an event if the view has changed
                                           (when-not (= last-view-hash new-view-hash)
                                             (d*/patch-elements! sse-gen new-view-str))
                                           new-view-hash)))
                                      recur))

                                    ; We want work cancelling to have higher priority.
                                    :priority true))))

                            hk-gen/on-close
                            (fn [sse-gen _]
                              (log/infof "Closing the connection to game %s." game-id)
                              (a/>!! <cancel :cancel)
                              (d*/close-sse! sse-gen))})))
