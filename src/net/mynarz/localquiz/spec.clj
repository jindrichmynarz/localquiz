(ns net.mynarz.localquiz.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [expound.alpha :as e]
            [fast-edn.core :as edn]
            [spec-tools.core :as st]))

(s/def ::list
  (st/spec {:spec (s/coll-of (s/and number? (complement neg?))
                             :kind vector?
                             :distinct true)
            :decode/string (fn [_ value]
                             (if (string/starts-with? value "[") ; TODO: This is awkward, but allows matching strings as strings.
                               (edn/read-string value)
                               value))}))

(s/def ::player-answer
  (s/or :boolean boolean?
        :int int?
        :double double?
        :list ::list
        :string string?))

(defn validate
  "Validate `data` according to a Clojure `spec`.
  Returns a validation report as a string if the validation fails."
  [spec data]
  (when-not (s/valid? spec data)
    (e/expound-str spec data)))
