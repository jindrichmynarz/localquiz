(ns net.mynarz.localquiz.views.player)

(defn join-game
  [{{:keys [game-id]} :path-params}]
  [:div
   {:action (str "/" game-id)
    :method "post"
    :name "join-game"}
   [:label
    {:for "player-name"}
    "Player name"]
   [:input
    {:id "player-name"
     :type "text"}]
   [:button
    {:data-on-click (format "@post('/join/%s')" game-id)
     :type "submit"}
    "Join game"]])

(defn player-view
  [request]
  [:main#morph
   [:h1 "Localquiz"]
   (join-game request)])
