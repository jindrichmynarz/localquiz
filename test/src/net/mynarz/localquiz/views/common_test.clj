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

(defn- occurrences
  [^String s ^String part]
  (count (re-seq (re-pattern (java.util.regex.Pattern/quote part)) s)))

(deftest network-answers
  (let [choices [{:label "House" :related ["Genre"]}
                 {:label "Techno" :related ["Genre"] :description "Detroit, emerged mid 80s."}
                 {:label "Acid House" :related ["House" "Techno"]}]
        html (h/html (views/answers-view fixtures/tr false {} fixtures/game-id false
                                         {:type :network :choices choices}))]
    (testing "The map survives morphs, which would reset what it reveals"
      (is (string/includes? html "class=\"network-map\" data-ignore-morph")))
    (testing "Every arc is drawn"
      (is (= 4 (occurrences html "class=\"nm-edge"))))
    (testing "Choices are selectable, and the root, which is none, is not"
      (is (= 3 (occurrences html "nm-choice")))
      (is (string/includes? html "class=\"nm-node nm-root\"")))
    (testing "Only the root and its children show at first"
      (is (string/includes? html "class=\"nm-node nm-choice nm-hidden\" data-id=\"Acid House\"")))
    (testing "The hint that the map pans shows, and is not announced"
      (is (string/includes? html "<div class=\"nm-hint\" aria-hidden")))
    (testing "The cell under the map hides until a choice is chosen, then shows it with its description"
      (is (string/includes? html "<div class=\"nm-info\" hidden>"))
      (is (= 3 (occurrences html "class=\"nm-chosen\"")))
      (is (string/includes? html (str "data-for=\"Techno\" hidden><strong>Techno</strong>"
                                      "<div class=\"description\">Detroit, emerged mid 80s.</div>"))))))

(def ^:private revealed-choices
  [{:label "House" :related ["Genre"]}
   {:label "Techno" :related ["Genre"] :description "Detroit, emerged mid 80s."}
   {:label "Acid House" :related ["House"]}
   {:label "Deep House" :related ["House"]}])

(defn- render-revealed
  "The moderator's revealed view of a :network question answered per `frequencies`."
  [frequencies]
  (h/html (views/answers-view fixtures/tr true
                              {:answer-count       (reduce + (vals frequencies))
                               :answer-frequencies frequencies
                               :answer-revealed?   true}
                              fixtures/game-id false
                              {:type :network :choices revealed-choices})))

(deftest network-result-tree
  (testing "The tree replaces the ranked list every other crowd-scored type gets"
    (let [html (render-revealed {"Acid House" 2 "Deep House" 1})]
      (is (not (string/includes? html "open-answers")))
      (is (string/includes? html "<svg class=\"nt\""))))
  (testing "It is fitted to its box, which a viewBox is what does"
    (is (string/includes? (render-revealed {"Acid House" 2 "Deep House" 1}) "viewBox=")))
  (testing "Nothing of the interactive map comes along"
    (let [html (render-revealed {"Acid House" 2 "Deep House" 1})]
      (is (not (string/includes? html "nm-")))
      (is (not (string/includes? html "network-map")))
      (is (not (string/includes? html "data-init")))))
  (testing "How many took an answer shows as the size of its circle, not as a number"
    (let [html (render-revealed {"Acid House" 2 "Deep House" 1})
          radius (fn [label]
                   (->> (re-seq (re-pattern (str "r=\"([0-9.]+)\"[^>]*></circle><text[^>]*>"
                                                 label))
                                html)
                        first second parse-double))]
      (is (not (string/includes? html "nt-count")))
      (testing "the label carries the name and nothing after it"
        (is (string/includes? html ">Acid House</text>")))
      (testing "the answer two took is drawn larger than the one only one took"
        (is (> (radius "Acid House") (radius "Deep House"))))))
  (testing "A node joining two answers is drawn and named: it is why they are apart"
    (let [html (render-revealed {"Acid House" 1 "Deep House" 1})]
      (is (= 3 (occurrences html "class=\"nt-node")))
      (is (= 2 (occurrences html "class=\"nt-edge")))
      ;; House holds the two together, so it is drawn, labelled, but not marked an answer.
      (is (string/includes? html ">House</text>"))
      (is (= 2 (occurrences html "nt-picked")))))
  (testing "Branches nobody picked are left out altogether"
    (let [html (render-revealed {"Acid House" 1 "Deep House" 1})]
      (is (not (string/includes? html "Techno")))
      ;; Genre is the root, and these two reach each other without it.
      (is (not (string/includes? html "Genre")))))
  (testing "The root is drawn, and labelled, when it is what holds the answers together"
    (let [html (render-revealed {"Techno" 1 "Deep House" 1})]
      (is (string/includes? html "Genre"))
      (is (string/includes? html "nt-root"))))
  (testing "One answer draws that answer alone, with no arcs"
    (let [html (render-revealed {"Techno" 3})]
      (is (= 1 (occurrences html "class=\"nt-node")))
      (is (zero? (occurrences html "class=\"nt-edge")))
      (is (string/includes? html ">Techno</text>"))))
  (testing "Everyone answering the same leaves a tree with no extent of its own"
    ;; It still has to be drawn at the size a node is drawn at anywhere else: sizes are a
    ;; fraction of the drawing, so a box collapsed onto the one node renders it as a speck.
    (let [html      (render-revealed {"Techno" 3})
          number    (fn [attribute]
                      (some-> (re-find (re-pattern (str attribute "=\"(-?[0-9.]+)\"")) html)
                              second parse-double))
          [_ _ w h] (map parse-double
                         (string/split (second (re-find #"viewBox=\"([^\"]+)\"" html)) #" "))]
      (testing "the box is the size a single ring of the layout is, and not square"
        (is (<= 100.0 w))
        (is (< h w)))
      (testing "the node and its label are drawn at the scale any other tree draws them"
        (is (< 1.0 (number "r")))
        (is (< 1.0 (number "font-size"))))))
  (testing "Nothing is drawn before the answers are in, or when nobody answered"
    (is (not (string/includes? (render-revealed {}) "<svg")))
    (is (not (string/includes?
               (h/html (views/answers-view fixtures/tr true
                                           {:answer-count 2
                                            :answer-frequencies {"Techno" 2}
                                            :answer-revealed? false}
                                           fixtures/game-id false
                                           {:type :network :choices revealed-choices}))
               "<svg")))))
