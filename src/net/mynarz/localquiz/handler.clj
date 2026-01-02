(ns net.mynarz.localquiz.handler
  (:require [net.mynarz.localquiz.routes :refer [routes]]
            [net.mynarz.localquiz.middleware :as middleware]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]))

(defn ->handler
  []
  (ring/ring-handler
   (ring/router
    routes
    {:data {:middleware [middleware/wrap-blocker
                         parameters/parameters-middleware
                         multipart/multipart-middleware
                         exception/exception-middleware
                         middleware/wrap-parse-signals
                         middleware/wrap-i18n
                         middleware/wrap-session]}})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))
