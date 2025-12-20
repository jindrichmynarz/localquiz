(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [net.mynarz.localquiz.views.common :refer [game-view]]
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
   [:button {:data:on-click "@post('/')"} "Upload"]])

(defn copy-button
  [^String join-game-url]
  [:span.copy-button-wrapper
   {:data-signals:_copy-label "['Copy', 'Copied!']"}
   [:button#copy-join-url
    {:data-on:mousedown (format "navigator.clipboard.writeText('%s');
                                $_copyLabel.reverse();
                                setTimeout(() => $_copyLabel.reverse(), 2000);"
                                join-game-url)
     :data-text "$_copyLabel[0]"}]])

(defn leaderboard
  [{game-id :sid}]
  [:section
   [:h1 "Leaderboard"]
   [:table
    [:tr [:th "Player"] [:th "Score"]]
    (for [{:keys [player-name score]} (game/leaderboard game-id)]
      [:tr [:td player-name] [:td score]])]
   [:a {:href "/"} "Play again"]])

(defmethod game-view [:moderator nil]
  [_]
  [:button.btn-primary#create-game
   {:data-on:mousedown "@post('/create')"} "Create a game"])

(defmethod game-view [:moderator :new]
  [{game-id :sid}]
  (let [play-game-url (str (:url config) "/play/" game-id)
        lobby (game/lobby game-id)
        has-enough-players? (game/has-enough-players? game-id)]
    [:div#sections
     [:section
      [:div
       [:div#qrcode (url->qrcode-svg play-game-url)]
       [:p
        [:input#game-url
         {:readonly true
          ;:size (count play-game-url)
          :type "text"
          :value play-game-url}]
        (copy-button play-game-url)]]
      [:div
       [:button.btn-primary
        {:data-on:click "@post('/start')"
         :disabled (not has-enough-players?)
         :type "submit"}
        "Start the game"]
       (when-not has-enough-players?
         [:p#waiting-for-players
          [:img {:src "img/wifi_exercise_animated.svg"}]
          "Waiting for at least two players to join..."])]]
     [:section
      (when (seq lobby)
        [:div
         [:h2 "Players"]
         [:ul
          (for [player-name lobby]
            [:li player-name])]])]]))

(defmethod game-view [:moderator :started]
  [_]
  [:h2 "The game has started!"])
