(ns net.mynarz.localquiz.views.moderator
  (:require [net.mynarz.localquiz.views.common :refer [view]]
            [net.mynarz.localquiz.config :refer [config]]
            [net.mynarz.localquiz.db :refer [db-conn]]
            [net.mynarz.localquiz.game :as game]
            [net.mynarz.localquiz.qrcode :refer [url->qrcode-svg]]
            [datahike.api :as d]
            [taoensso.timbre :as log]))

(defn pick-questions
  [{game-id :sid}]
  (view [:div
         [:h1 "Localquiz"]
         [:label
          {:for "questions-upload"}
          "Upload questions"]
         [:input
          {:accept ".edn"
           :id "questions-upload"
           :type "file"}]
         [:button {:data-on-click "@post('/')"} "Upload"]]))

(defn ->join-game-url
  "Format the URL to join the game identified by `game-id`."
  [^String game-id]
  (let [{:keys [host-name port]} config]
    (format "https://%s:%d/join/%s" host-name port game-id)))

(defn lobby
  "Get the players waiting in the lobby for the game identified by `game-id`.
  The players are sorted in the chronological order according to when they joined the game."
  [^String game-id]
  (->> game-id
       (d/q '[:find ?player-name ?time-joined
              :keys player-name time-joined
              :in $ ?game-id
              :where [?game :game/id ?game-id]
              [?game :game/players ?player]
              [?player :player/name ?player-name]
              [?player :player/time-joined ?time-joined]]
            @db-conn)
       (sort-by :time-joined)
       (map :player-name)))

(defn start-game
  [{game-id :sid
    :as request}]
  (let [join-game-url (->join-game-url game-id)
        lobby (lobby game-id)]
    (view [:div
           [:section
            [:h2
             [:a
              {:href join-game-url}
              "Join the game"]
             [:pre join-game-url]
             [:button#copy-join-url
              {:data-on-mousedown (format "navigator.clipboard.writeText('%s')" join-game-url)}
              "Copy"]]
            (url->qrcode-svg join-game-url)
            [:button
             {:data-on-click "@post('/start-game')"
              :type "submit"}
             "Start the game"]]
           [:section
            [:h2 "Players"]
            [:ul
             (for [{player-name :player/name} lobby]
               [:li player-name])]]])))

(defn leaderboard
  [{game-id :sid
    :as request}]
  (view [:section
         [:h1 "Leaderboard"]
         [:table
          [:tr [:th "Player"] [:th "Score"]]
          (for [{:keys [player-name score]} (game/leaderboard game-id)]
            [:tr [:td player-name] [:td score]])]
         [:a {:href "/"} "Play again"]])) ; TODO: Reset the session ID and end-game!
