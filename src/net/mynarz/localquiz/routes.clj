(ns net.mynarz.localquiz.routes
  (:require [net.mynarz.localquiz.sse :as sse]
            [net.mynarz.localquiz.views.common :as views]
            [net.mynarz.localquiz.actions.moderator :as moderator-actions]
            [net.mynarz.localquiz.actions.player :as player-actions]))

(def routes
  [; Moderator's routes
   ["/" {:get views/shim-view
         :post (partial sse/handler views/patch-view)}]
   ["/create" {:post (partial views/view moderator-actions/create-game!)}]
   ["/start" {:post (partial views/view moderator-actions/start-game!)}]
   ; Players' routes
   ["/play/:game-id" {:get views/shim-view
                      :post (partial sse/handler views/patch-view)}]
   ["/join/:game-id"
    ["" {:post (partial views/view player-actions/join-game!)}]
    ["/validate" {:post (partial views/view player-actions/validate-player-name)}]]])
