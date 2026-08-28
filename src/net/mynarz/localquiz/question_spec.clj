(ns net.mynarz.localquiz.question-spec
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [datahike.config :as datahike])
  (:import (java.net URL)))

(def disallowed-tags
  "Hiccup element tags rejected in question content: they can run scripts, load
  external resources, redirect the page, or phish. Media and formatting tags
  (e.g. :audio, :video, :img) are intentionally allowed."
  #{"script" "iframe" "object" "embed" "applet"
    "base" "meta" "link" "style"
    "frame" "frameset" "form" "svg"})

(defn safe-tag?
  "A Hiccup tag keyword whose element name is not in `disallowed-tags`
  (case-insensitive, ignoring #id/.class shorthand)."
  [tag]
  (and (keyword? tag)
       (-> tag name string/lower-case (string/split #"[#.]") first disallowed-tags nil?)))

(def url-attrs
  "Attribute names whose value is a URL, where a javascript: scheme would execute."
  #{"href" "src" "action" "formaction" "xlink:href"
    "cite" "poster" "background" "ping" "longdesc"})

(defn- code-attr?
  "True if attribute key `k` runs code on an event — a native on* handler or a
  Datastar data-* attribute (case-insensitive)."
  [k]
  (let [attr (string/lower-case (name k))]
    (or (string/starts-with? attr "on")
        (string/starts-with? attr "data"))))

(defn- javascript-url?
  "True if string `v` is a javascript: URL, tolerating leading or embedded
  whitespace, control characters, and case."
  [v]
  (and (string? v)
       (-> v
           (string/replace #"[\x00-\x20]+" "")
           string/lower-case
           (string/starts-with? "javascript:"))))

(defn safe-attrs?
  "An attribute map with no event/data-* keys and no javascript: URL values."
  [m]
  (and (map? m)
       (not-any? (fn [[k v]]
                   (or (code-attr? k)
                       (and (contains? url-attrs (string/lower-case (name k)))
                            (javascript-url? v))))
                 m)))

(defn valid-question-length?
  "Test if `question` serialized to EDN fits within the maximum string length Datahike can store."
  [question]
  (let [question-length (-> question pr-str count)]
    (<= question-length (:max-string-length datahike/default-value-caps))))

(s/def ::hiccup
  (s/or :string string?
        :element (s/cat :tag safe-tag?
                        :attrs (s/? safe-attrs?)
                        :content (s/* ::hiccup))))

(s/def ::text ::hiccup)

(s/def ::correct? boolean?)

(s/def ::answer string?)

(s/def ::percentage
  (s/and number? #(<= 0 % 100)))

(s/def ::label string?)

(s/def ::description ::hiccup)

(s/def ::choice
  (s/or ;; A multiple-choice option (see the :multiple question).
        :choice (s/keys :req-un [::text]
                        :opt-un [::correct?])
        ;; A labelled option (e.g. an :autocomplete suggestion).
        :option (s/keys :req-un [::label]
                        :opt-un [::description])))

(s/def ::choices
  ;; Non-conforming so the raw choice maps survive conformation (the ::choice
  ;; s/or would otherwise tag them), letting predicates like :multiple's read them.
  (s/nonconforming
    (s/coll-of ::choice
               :min-count 2
               :distinct true)))

(s/def ::note ::hiccup)

(s/def ::question-base
  (s/and
    (s/keys :req-un [::text]
            :opt-un [::note])
    valid-question-length?))

(defmulti question :type)

(defmethod question :yesno [_]
  (s/keys :opt-un [::correct?]))

(defmethod question :multiple [_]
  (s/and (s/keys :req-un [::choices])
         ;; Multiple-choice options are rendered by their :text.
         #(every? :text (:choices %))))

(defmethod question :open [_]
  (s/keys :req-un [::answer]))

(s/def ::threshold
  (s/and number? (comp not neg?)))

(defmethod question :percent-range [_]
  (s/keys :req-un [::percentage]
          :opt-un [::threshold]))

(defmethod question :player-choice [_]
  (comp #{:consensus} :scoring))

(defmethod question :autocomplete [_]
  ;; Suggestions come from :choices; the typed-or-picked answer is scored across
  ;; players (consensus/majority), since there is no per-question correct value.
  (s/and (s/keys :req-un [::choices])
         (comp #{:consensus :majority} :scoring)))

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
