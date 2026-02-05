(ns net.mynarz.localquiz.routes
  (:require [net.mynarz.localquiz.actions.moderator :as moderator-actions]
            [net.mynarz.localquiz.actions.player :as player-actions]
            [net.mynarz.localquiz.spec :as s]
            [net.mynarz.localquiz.sse :as sse]
            [net.mynarz.localquiz.views.common :as views]
            [net.mynarz.localquiz.views.moderator] ; Required to load the moderator-specific methods of views/view.
            [net.mynarz.localquiz.views.player] ; Required to load the player-specific methods of view/view.
            [reitit.ring.middleware.multipart :as multipart]))

(def morph-view
  (partial sse/handler views/morph-view))

(def routes
  [; Moderator's routes
   ["/" {:get views/shim-view}]
   ["/sse" {:get morph-view}]
   ["/create"
    ; FIXME: Avoid the duplication combining views/morph-body with views/views.
    ["" {:post {:handler (comp views/view (partial views/morph-body moderator-actions/create-game!))
                :parameters {:form {:number-of-questions ::s/number-of-questions}
                             :multipart {:question-file multipart/temp-file-part}}}}]
    ["/validate" {:post {:handler (comp views/view (partial views/morph-body moderator-actions/validate-questions))
                         :parameters {:multipart {:question-file multipart/temp-file-part}}}}]]
   ["/question" {:post (comp views/view moderator-actions/next-question!)}]
   ["/leaderboard" {:post (comp views/view moderator-actions/leaderboard!)}]
   ["/end" {:post (comp views/view moderator-actions/end-game!)}]

   ; Players' routes
   ["/play/:game-id" {:get views/shim-view}]
   ["/sse/:game-id" {:get morph-view}]
   ["/join/:game-id"
    ["" {:post (comp views/view player-actions/join-game!)}]
    ["/validate" {:post (comp views/view player-actions/validate-player-name)}]]
   ["/answer/:game-id" {:post (comp views/view player-actions/answer-question!)}]])
