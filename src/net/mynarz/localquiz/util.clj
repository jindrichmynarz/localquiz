(ns net.mynarz.localquiz.util
  (:require [charred.api :as charred]
            [fast-edn.core :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (java.io PushbackReader)))

(def ^:private buf-size 1024)

(defn long-str
  [& strings]
  (string/join "\n" strings))

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
