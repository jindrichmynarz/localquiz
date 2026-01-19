(ns net.mynarz.localquiz.handler
  (:require [net.mynarz.localquiz.i18n :as i18n]
            [net.mynarz.localquiz.middleware :as middleware]
            [net.mynarz.localquiz.routes :refer [routes]]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as ring-coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [taoensso.tempura :as tempura]))

(defn ->handler
  []
  (ring/ring-handler
   (ring/router
    routes
    {:data {:middleware [middleware/wrap-blocker
                         parameters/parameters-middleware
                         ring-coercion/coerce-exceptions-middleware
                         ring-coercion/coerce-request-middleware
                         middleware/wrap-multipart
                         middleware/wrap-parse-signals
                         middleware/wrap-language
                         [tempura/wrap-ring-request {:tr-opts {:dict i18n/dictionary}}]
                         middleware/wrap-session
                         exception/exception-middleware]}})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))
