(ns net.mynarz.localquiz.util
  (:require [charred.api :as charred]
            [dev.onionpancakes.chassis.core :as h]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.walk :refer [postwalk-replace]]
            [fast-edn.core :as edn])
  (:import (clojure.lang RT)
           (java.io PushbackReader)
           (java.text DecimalFormat
                      DecimalFormatSymbols)
           (java.util ArrayList
                      Collection
                      Collections
                      Locale
                      Random)))

(def ^:private buf-size 1024)

(def decimal-format
  "Format doubles as decimal numbers with up to 2 decimal places."
  ; FIXME: Set locale based on `tr`?
  (let [formatter (DecimalFormat. "0.##" (DecimalFormatSymbols/getInstance Locale/US))]
    (fn [^double n]
      (.format formatter n))))

(defn descending-order
  "Sort `a` and `b` in the descending order."
  [a b]
  (compare b a))

(defn deterministic-shuffle
  "Shuffle `coll`, always the same."
  [^Collection coll]
  (let [array-list (ArrayList. coll)]
    (Collections/shuffle array-list (Random. (hash coll)))
    (RT/vector (.toArray array-list))))

(defn long-str
  [& strings]
  (->> strings
       (remove nil?)
       (string/join "\n")))

(defn read-edn-resource
  "Read EDN `resource` from the classpath."
  [^String resource]
  (-> resource
      io/resource
      io/reader
      PushbackReader.
      edn/read-once))

(def read-json
  "Read JSON, keywordizing its keys."
  (charred/parse-json-fn {:async? false
                          :bufsize buf-size
                          :key-fn keyword}))

(defn replace-react-fragments
  "Replace React fragments (:<>) in `hiccup` with :div elements."
  [hiccup]
  (postwalk-replace {:<> :div} hiccup))

(defmacro svg
  "Load SVG `resource`."
  [^String resource]
  (->> resource
       (str "img/svg/")
       io/resource
       slurp
       h/raw))

(defmacro thread
  "Starts a virtual thread. Conveys bindings."
  [& body]
  `(Thread/startVirtualThread
    (bound-fn* ;; binding conveyance
     (fn [] ~@body))))

(defmacro while-some
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings & body]
  `(loop []
     (when-some ~bindings
       ~@body
       (recur))))
