(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [net.mynarz.localquiz.views.common :refer [answers-view game-view timer]]
            [taoensso.timbre :as log]))

(defn pick-questions
  [{game-id :sid}]
  [:div
   [:h1 "Localquiz"]
   [:label
    {:for "questions-upload"}
    "Upload questions"]
   [:input
    {:accept ".edn"
     :id "questions-upload"
     :type "file"}]
   [:button.btn {:data:on-click "@post('/')"} "Upload"]])

(defn copy-button
  [^String join-game-url]
  [:span.copy-button-wrapper
   {:data-signals:_copy-label "['Copy', 'Copied!']"}
   [:button.btn#copy-join-url
    {:data-on:mousedown (format "navigator.clipboard.writeText('%s');
                                $_copyLabel.reverse();
                                setTimeout(() => $_copyLabel.reverse(), 2000);"
                                join-game-url)
     :data-text "$_copyLabel[0]"}]])

(def end-game
  [:span#end-game
   {:data-on:click "confirm('Do you want to end the game?') && @post('/end')"
    :title "End the game"}
   [:i.material-icons.md-light.md-36 "cancel"]])

(defn leaderboard
  [^String game-id]
  [:div#leaderboard
   [:h2 "Leaderboard"]
   [:table
    [:tr [:th "Player"] [:th "Score"]]
    (for [{:keys [player-name score]} (game/leaderboard game-id)]
      [:tr [:td player-name] [:td score]])]])

(def play-again
  [:button.btn
   {:data-on:click "@post('/end)"}
   [:i.material-icons.md-24 "replay"]
   "Play again"])

(defmethod game-view [:moderator nil]
  [_]
  [:section#create-game
   [:button.btn.btn-primary
    {:data-on:mousedown "@post('/create')"} "Create a game"]])

(defmethod game-view [:moderator :new]
  [{game-id :sid}]
  (let [play-game-url (str (:url config) "/play/" game-id)
        lobby (game/lobby game-id)
        has-enough-players? (game/has-enough-players? game-id)]
    [:div#sections
     end-game
     [:section#join-game
      [:div
       [:div#qrcode (url->qrcode-svg play-game-url)]
       [:p
        [:input#game-url
         {:readonly true
          :type "text"
          :value play-game-url}]
        (copy-button play-game-url)]
       (if has-enough-players?
         [:p
          [:button.btn.btn-primary
           {:data-on:click "@post('/start')"
            :disabled (not has-enough-players?)
            :type "submit"}
           "Start the game"]]
         [:p#waiting-for-players
          [:img {:src "img/wifi_exercise_animated.svg"}]
          "Waiting for at least two players to join..."])]]
     (when (seq lobby)
       [:section#lobby
        [:h2 "Players"]
        [:ul
         (for [player-name lobby]
           [:li player-name])]])]))

(defmethod game-view [:moderator :question]
  [{game-id :sid}]
  (let [answer-revealed? (game/all-players-answered? game-id)
        current-question (game/current-question game-id)]
     [:section#content
      end-game
      [:div#question
       (:text current-question)
       (answers-view true
                     answer-revealed?
                     game-id
                     current-question)]]))

(defmethod game-view [:moderator :show-answers]
  [])

(defmethod game-view [:moderator :leaderboard]
  [{game-id :sid}]
  [:section#content
   end-game
   (leaderboard game-id)])

(defmethod game-view [:moderator :end]
  [{game-id :sid}]
  [:section#content
   (leaderboard game-id)
   play-again])
