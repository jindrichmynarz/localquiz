(ns net.mynarz.localquiz.question-spec
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk])
  (:import (java.net URL)))

(s/def ::hiccup
  (s/or :string string?
        :element (s/cat :tag keyword?
                        :attrs (s/? map?)
                        :content (s/* ::hiccup))))

(s/def ::text ::hiccup)

(s/def ::correct? boolean?)

(s/def ::answer string?)

(s/def ::percentage
  (s/and number? #(<= 0 % 100)))

(s/def ::choice
  (s/keys :req-un [::text]
          :opt-un [::correct?]))

(s/def ::choices
  (s/and
    (s/coll-of ::choice
               :min-count 2
               :distinct true)))

(s/def ::note ::hiccup)

(s/def ::question-base
  (s/keys :req-un [::text]
          :opt-un [::note]))

(defmulti question :type)

(defmethod question :yesno [_]
  (s/keys :opt-un [::correct?]))

(defmethod question :multiple [_]
  (s/keys :req-un [::choices]))

(defmethod question :open [_]
  (s/keys :req-un [::answer]))

(s/def ::threshold
  (s/and number? pos?))

(defmethod question :percent-range [_]
  (s/keys :req-un [::percentage]
          :opt-un [::threshold]))

(defmethod question :player-choice [_]
  (comp #{:consensus} :scoring))

(s/def ::sort-value
  number?)

(s/def ::item
  (s/keys :req-un [::text]
          :opt-un [::sort-value]))

(s/def ::items
  (s/coll-of ::item
             :kind vector?
             :min-count 2
             :distinct true))

(defmethod question :sort [_]
  (s/keys :req-un [::items]))

(s/def ::question
  (s/and
    ::question-base
    (s/multi-spec question :type)))

(s/def ::questions
  (s/coll-of ::question
             :min-count 1
             :distinct true))

(s/def ::name string?)

(def http?
  (partial re-matches #"^https?:\/\/.*$"))

(defn url?
  "Test if `s` is a valid URL."
  [^String s]
  (try
    (.toURI (URL. s))
    true
    (catch Exception _ false)))

(s/def ::url (s/and string? http? url?))

(s/def ::creator
  (s/keys :req-un [::name]
          :opt-un [::url]))

(s/def ::creators
  (s/coll-of ::creator
             :min-count 1
             :distinct true))

(s/def ::defs (s/map-of keyword? any?))

(s/def ::data
  (s/keys :req-un [::questions]
          :opt-un [::creators ::defs]))

(defn resolve-refs
  "Walk `data`, replacing each {:ref id} node with the value from `defs`.
  Namespaced keywords do a two-level look-up: namespace key first, name key second.
  Throws ex-info on unknown IDs."
  [defs data]
  (walk/postwalk
    (fn [node]
      (if (and (map? node) (= #{:ref} (set (keys node))))
        (let [ref-id (:ref node)
              value (if-let [ref-ns (namespace ref-id)]
                      (get-in defs [(keyword ref-ns) (keyword (name ref-id))])
                      (get defs ref-id))]
          (if (some? value)
            value
            (throw (ex-info (str "Undefined ref: " (pr-str ref-id))
                            {:ref ref-id}))))
        node))
    data))
