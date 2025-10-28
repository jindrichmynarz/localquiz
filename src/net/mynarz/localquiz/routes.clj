(ns net.mynarz.localquiz.routes
  (:require [net.mynarz.localquiz.sse :as sse]
            [net.mynarz.localquiz.views.common :as views]
            [net.mynarz.localquiz.actions.moderator :as moderator-actions]
            [net.mynarz.localquiz.views.moderator :as moderator-views]
            [net.mynarz.localquiz.actions.player :as player-actions]
            [net.mynarz.localquiz.views.player :as player-views]))

(def routes
  [; Moderator's routes
   ["/" {:get (fn [_] views/shim-view)
         :post (partial sse/handler moderator-views/moderator-view)}]
   ; Players' routes
   ["/:game-id" {:get (fn [_] views/shim-view)
                 :post (partial sse/handler player-views/player-view)}]
   ["/join/:game-id" {:post player-actions/join-game!}]])
