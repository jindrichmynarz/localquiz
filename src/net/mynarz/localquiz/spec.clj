(ns net.mynarz.localquiz.spec
  (:require [net.mynarz.localquiz.crypto :refer [random-unguessable-uid]]
            [clojure.spec.alpha :as s]))

(s/def ::non-empty-string
  (s/and string? seq))

(s/def ::hiccup
  (s/or :string string?
        :element (s/cat :tag keyword?
                        :attrs (s/? map?)
                        :content (s/* ::hiccup))))

(s/def ::text ::hiccup)

(s/def ::note ::hiccup)

(s/def ::correct? boolean?)

(s/def ::choice
  (s/keys :req-un [::text]
          :opt-un [::correct?]))

(s/def ::choices
  (s/and
    (s/coll-of ::choice
               :min-count 2
               :distinct true)
    (partial some ::correct?)))

(s/def ::question-base
  (s/keys :req-un [::text]
          :opt-un [::note]))

(defmulti question :type)

(defmethod question :multiple [_]
  (s/keys :req-un [::choices]))

(s/def ::question
  (s/and
    ::question-base
    (s/multi-spec question :type)))

(s/def ::questions
  (s/coll-of ::question
             :distinct true))

(s/def ::answer
  (s/keys :req [::correct?])) ; TODO: The answer can track more: what was the actual answer, score, etc.

(s/def ::answers
  (s/coll-of ::answer))

(s/def ::name ; TODO: Implement validation prohibiting players with the same names
  ::non-empty-string)

(s/def ::player-id ; TODO: Same as the client's session ID?
  (s/spec ::non-empty-string
          :gen random-unguessable-uid))

(s/def ::player
  (s/keys :req [::name] ; TODO: Allow to pick player's colour or avatar (or select randomly based on a modulo hash of the player's ID? How to avoid hash collisions?
          :opt [::answers]))

(s/def ::players
  (s/map-of ::player-id ::player))

(s/def ::game-id ; TODO: Same as the moderator's session ID?
                 ;       Should be a signal in the player view?
  (s/spec ::non-empty-string
          :get random-unguessable-uid))

(s/def ::game
  (s/keys :opt [::questions
                ::players]))

(s/def db
  (s/map-of ::game-id ::game))

(comment
  (def db
    {"MWfvlS3tbFg29yW2fekQW-8CsTM"
     {::questions [{:type :multiple
                    :text [:<>
                           [:audio {:autoPlay "autoplay"
                                    :src "https://cdn.freesound.org/previews/155/155115_199526-lq.mp3"}]
                           [:p "What is the name of the animal making this sound?"]]
                    :choices [{:text "Ferret" :correct? true}
                              {:text "Makak rhesus"}
                              {:text "Chipmunk"}
                              {:text "Your drunken uncle"}]
                    :note [:p
                           [:a {:href "https://freesound.org/s/155115/"}
                               "Ferret by J.Zazvurek"]
                           " - License: Attribution 4.0"]}]
      ::players
      {"P4kwZhyODiHWkDGILIEakAo-84Q" {::name "Franta"
                                      ::answers [{::correct? false}]}
       "s7yh25pi-aq_4pHLX55qkW-aAb8" {::name "Pepa"
                                      ::answers [{::correct? true}]}}}}))
