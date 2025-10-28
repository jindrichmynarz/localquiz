(ns net.mynarz.localquiz.views.player)

(defn join-game
  [{{:keys [game-id]} :path-params}]
  [:form
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
    {:type "submit"}
    "Join game"]])

(defn player-view
  [{{:keys [game-id]} :path-params
    :as request}]
  [:main#morph [:h1 "Localquiz"]])
