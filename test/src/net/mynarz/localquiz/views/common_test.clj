(ns net.mynarz.localquiz.views.common-test
  (:require [net.mynarz.localquiz.crypto :as crypto]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.test-fixtures :as fixtures]
            [net.mynarz.localquiz.views.common :as views]
            [clojure.string :as string]
            [clojure.test :refer [are deftest is testing use-fixtures]]
            [dev.onionpancakes.chassis.core :as h]))

(use-fixtures :once fixtures/test-db)

(deftest search-options
  (let [options [{:label "Nové Vinohrady"}
                 {:label "Žižkov" :description "Prague 3"}
                 {:label "Vinohrady"}
                 {:text "Karlín"}]]
    (are [fragment labels] (= (map views/option-label (views/search-options options fragment)) labels)
         "vino"   ["Vinohrady" "Nové Vinohrady"] ; Prefix matches come first
         "ZIZ"    ["Žižkov"]      ; Case and diacritics are normalized away
         "zizkov" ["Žižkov"]
         "karlin" ["Karlín"]      ; A :text option is labelled by its :text
         ; No prefix match, so the question's own order is kept
         "r"      ["Nové Vinohrady" "Vinohrady" "Karlín"]
         "bork"   []
         ; An empty fragment is the player asking to see everything
         ""       ["Nové Vinohrady" "Žižkov" "Vinohrady" "Karlín"]
         "  "     ["Nové Vinohrady" "Žižkov" "Vinohrady" "Karlín"]
         ; A missing fragment means the list was never opened
         nil      [])))

(defn render-autocomplete
  "Render the autocomplete widget for a player who has typed `fragment`,
  or has not opened the list at all when it is not given."
  ([]
   (render-autocomplete nil))
  ([^String fragment]
   (let [session-id (crypto/random-unguessable-uid)
         options [{:label "Ambient" :description "Slow and atmospheric"}
                  {:label "Techno"}]]
     (when fragment
       (game/set-search-fragment! session-id fragment))
     (h/html (views/autocomplete fixtures/tr fixtures/game-id session-id options)))))

(deftest autocomplete
  (testing "A matching suggestion shows its label and its description"
    (let [html (render-autocomplete "amb")]
      (is (string/includes? html "data-option=\"Ambient\""))
      (is (string/includes? html "<span class=\"description\">Slow and atmospheric</span>"))
      (is (not (string/includes? html "Techno")))))
  (testing "A suggestion without a description shows only its label"
    (let [html (render-autocomplete "tech")]
      (is (string/includes? html "data-option=\"Techno\""))
      (is (not (string/includes? html "description")))))
  (testing "The list is closed until the player opens it"
    (is (not (string/includes? (render-autocomplete) "<ul"))))
  (testing "Revealing the options lists them all"
    (let [html (render-autocomplete "")]
      (is (string/includes? html "data-option=\"Ambient\""))
      (is (string/includes? html "data-option=\"Techno\""))))
  (testing "The reveal control is always offered"
    (is (every? #(string/includes? % "class=\"reveal-options\"")
                [(render-autocomplete) (render-autocomplete "amb")]))))
