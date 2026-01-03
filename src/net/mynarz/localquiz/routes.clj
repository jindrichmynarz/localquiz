(ns net.mynarz.localquiz.routes
  (:require [net.mynarz.localquiz.sse :as sse]
            [net.mynarz.localquiz.views.common :as views]
            [net.mynarz.localquiz.views.moderator] ; Require to load the moderator-specific methods of views/view.
            [net.mynarz.localquiz.views.player] ; Require to load the player-specific methods of view/view.
            [net.mynarz.localquiz.actions.moderator :as moderator-actions]
            [net.mynarz.localquiz.actions.player :as player-actions]))

(def morph-view
  (partial sse/handler views/morph-view))

(def routes
  [; Moderator's routes
   ["/" {:get views/shim-view}]
   ["/sse" {:get morph-view}]
   ["/create"
    ["" {:post (partial views/view moderator-actions/create-game!)}]
    ["/validate" {:post partial views/view moderator-actions/validate-questions}]]
   ["/question" {:post (partial views/view moderator-actions/next-question!)}]
   ["/leaderboard" {:post (partial views/view moderator-actions/leaderboard!)}]
   ["/end" {:post (partial views/view moderator-actions/end-game!)}]

   ; Players' routes
   ["/play/:game-id" {:get views/shim-view}]
   ["/sse/:game-id" {:get morph-view}]
   ["/join/:game-id"
    ["" {:post (partial views/view player-actions/join-game!)}]
    ["/validate" {:post (partial views/view player-actions/validate-player-name)}]]
   ["/answer/:game-id" {:post (partial views/view player-actions/answer-question!)}]])
