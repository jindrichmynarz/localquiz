(ns net.mynarz.localquiz.routes
  (:require [net.mynarz.localquiz.actions.moderator :as moderator-actions]
            [net.mynarz.localquiz.actions.player :as player-actions]
            [net.mynarz.localquiz.spec :as spec]
            [net.mynarz.localquiz.sse :as sse]
            [net.mynarz.localquiz.views.common :as views]
            [net.mynarz.localquiz.views.moderator] ; Required to load the moderator-specific methods of views/view.
            [net.mynarz.localquiz.views.player] ; Required to load the player-specific methods of view/view.
            [starfederation.datastar.clojure.adapter.http-kit2 :as hk-adapter]))

(def routes
  [; Moderator's routes
   ["/" {:get views/shim-view}]
   ["/sse" {:get sse/handler
            :middleware [hk-adapter/start-responding-middleware]}]
   ["/create"
    ["" {:post {:handler (comp views/view moderator-actions/create-game!)
                :parameters {:form ::spec/question-form-params
                             :multipart ::spec/question-file-params}}}]
    ["/validate" {:post {:handler (comp views/view moderator-actions/validate-questions!)
                         :parameters {:form ::spec/question-form-params
                                      :multipart ::spec/question-file-params}}}]]
   ["/host/:game-id" {:get views/shim-view}]
   ["/next/:game-id" {:post (comp views/view moderator-actions/next!)}]
   ["/end/:game-id" {:post (comp views/view moderator-actions/end-game!)}]

   ; Players' routes
   ["/play/:game-id" {:get views/shim-view}]
   ["/sse/:game-id" {:get sse/handler
                     :middleware [hk-adapter/start-responding-middleware]}]
   ["/join/:game-id"
    ["" {:post {:handler (comp views/view player-actions/join-game!)
                :parameters {:form ::spec/player-params}}}]
    ["/validate" {:post {:handler (comp views/view player-actions/validate-player-name!)
                         :parameters {:form ::spec/player-params}}}]]
   ["/answer/:game-id" (comp views/view player-actions/answer-question!)]
   ["/leave/:game-id" (comp views/view player-actions/leave-game!)]])
