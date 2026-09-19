(ns net.mynarz.localquiz.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [expound.alpha :as e]
            [fast-edn.core :as edn]
            [reitit.ring.middleware.multipart :as multipart]
            [spec-tools.core :as st]))

(s/def ::list
  (st/spec {:spec (s/coll-of (s/and number? (complement neg?))
                             :kind vector?
                             :distinct true)
            :decode/string (fn [_ value]
                             ; TODO: This is awkward, but allows matching strings as strings.
                             (if (string/starts-with? value "[")
                               (edn/read-string value)
                               value))}))

(s/def ::number-of-questions
  (s/and int? pos?))

(s/def ::player-answer
  (s/or :boolean boolean?
        :int int?
        :double double?
        :list ::list
        :string string?))

(s/def ::question-file
   multipart/temp-file-part)

(s/def ::question-source string?)

(s/def ::question-file-params
  (s/keys :opt-un [::number-of-questions
                   ::question-file]))

(s/def ::question-form-params
  (s/keys :opt-un [::number-of-questions
                   ::question-source]))

(s/def ::player-name
  (s/and string? #(<= 1 (count %) 20)))

(s/def ::player-params
  (s/keys :req-un [::player-name]))

(def ^:private max-problems
  "Most problems a validation report describes. A question set may repeat one problem in
  thousands of questions, as when they share invalid choices."
  10)

(defn- step
  "The part of `x` at `k` of a spec problem's path, which indexes into a set by its order."
  [x k]
  (if (and (int? k) (not (associative? x)))
    (nth (seq x) k nil)
    (get x k)))

(defn- in-entry
  "`problem` of `value` as explain data of the entry it is in, two steps down its path, such
  as a question of a question set. Expound shows a problem within the whole value, which
  takes it seconds on thousands of questions."
  [explained value problem]
  (let [[path more] (split-at 2 (:in problem))]
    (assoc explained
           ::s/value    (reduce step value path)
           ::s/problems [(assoc problem :in (vec more))])))

(defn validate
  "Validate `data` according to a Clojure `spec`.
  Returns a validation report as a string if the validation fails, describing the first
  `max-problems` problems, each within the entry of `data` it is in.
  Long collections are elided: expound prints the whole value as context for every
  problem, which on a question set with its refs resolved runs into megabytes."
  [spec data]
  (when-not (s/valid? spec data)
    (let [explained (s/explain-data spec data)
          problems  (::s/problems explained)
          more      (- (count problems) max-problems)]
      (binding [*print-length* 10]
        (str (with-out-str
               (doseq [problem (take max-problems problems)]
                 (e/printer (in-entry explained data problem))))
             (when (pos? more)
               (format "\n... and %d more problems." more)))))))
