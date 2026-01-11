(ns net.mynarz.localquiz.handler
  (:require [net.mynarz.localquiz.i18n :as i18n]
            [net.mynarz.localquiz.middleware :as middleware]
            [net.mynarz.localquiz.routes :refer [routes]]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [taoensso.tempura :as tempura]))

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
                         middleware/wrap-language
                         [tempura/wrap-ring-request {:tr-opts {:dict i18n/dictionary}}]
                         middleware/wrap-session]}})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))
