(ns net.mynarz.localquiz.handler
  (:require [net.mynarz.localquiz.i18n :as i18n]
            [net.mynarz.localquiz.middleware :as middleware]
            [net.mynarz.localquiz.routes :refer [routes]]
            [reitit.coercion.spec :as coercion-spec]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as ring-coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [taoensso.tempura :as tempura]
            [taoensso.timbre :as log]))

(def exception-middleware
  (exception/create-exception-middleware
    (merge exception/default-handlers
           {::exception/wrap (fn [handler exception request]
                               (log/error exception)
                               (handler exception request))})))

(defn ->handler
  []
  (ring/ring-handler
   (ring/router
    routes
    {:data {:coercion coercion-spec/coercion
            :middleware [middleware/wrap-blocker
                         parameters/parameters-middleware
                         middleware/wrap-parse-signals
                         middleware/wrap-language
                         [tempura/wrap-ring-request {:tr-opts {:dict i18n/dictionary}}]
                         middleware/wrap-session
                         exception-middleware
                         ring-coercion/coerce-exceptions-middleware
                         ;; Coercing response body
                         ring-coercion/coerce-response-middleware
                         ;; Coercing request parameters
                         ring-coercion/coerce-request-middleware
                         ;; Multipart middleware must be used after the coercion middleware,
                         ;; because that overwrites the parameters.
                         middleware/wrap-multipart]}})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))
