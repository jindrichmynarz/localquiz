(ns net.mynarz.localquiz.routes
  (:require [net.mynarz.localquiz.sse :as sse]
            [net.mynarz.localquiz.views.common :as views]
            [net.mynarz.localquiz.views.moderator] ; Require to load the moderator-specific methods of views/view.
            [net.mynarz.localquiz.views.player] ; Require to load the player-specific methods of view/view.
            [net.mynarz.localquiz.actions.moderator :as moderator-actions]
            [net.mynarz.localquiz.actions.player :as player-actions]))

(def routes
  [; Moderator's routes
   ["/" {:get views/shim-view
         :post (partial sse/handler views/patch-view)}]
   ["/create" {:post (partial views/view moderator-actions/create-game!)}]
   ["/start" {:post (partial views/view moderator-actions/start-game!)}]
   ["/end" {:post (partial views/view moderator-actions/end-game!)}]
   ; Players' routes
   ["/play/:game-id" {:get views/shim-view
                      :post (partial sse/handler views/patch-view)}]
   ["/join/:game-id"
    ["" {:post (partial views/view player-actions/join-game!)}]
    ["/validate" {:post (partial views/view player-actions/validate-player-name)}]]
   ["/answer/:game-id" {:post (partial views/view player-actions/answer-question!)}]])
