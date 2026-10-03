(ns net.mynarz.localquiz.async
  (:require [net.mynarz.localquiz.util :as util]
            [clojure.core.async :as a]
            [mount.core :refer [defstate]]))

(defn throttle
  "Throttle `<in-ch` by a number of `msec` to avoid rapid consequent events."
  [^Integer msec
   <in-ch]
  (let [; No buffer on the out-ch as the in-ch should be buffered
        <out-ch (a/chan)]
    (util/thread
      (util/while-some [event (a/<!! <in-ch)]
                       (a/>!! <out-ch event)
                       (Thread/sleep ^long msec)))
    <out-ch))

(defstate refresh-channel
  "Async channel for refresh signals."
  :start (a/chan 64))

(defn topic
  "Topic of a refresh `event`: its session, and whether it is an event the client must
  receive (:event) or a request to re-render (:render)."
  [{:keys [redirect session-id signals]}]
  [session-id (if (or redirect signals) :event :render)])

(defstate refresh-pub
  "Publication of refresh signals for each session ID."
  :start (a/pub refresh-channel topic))
