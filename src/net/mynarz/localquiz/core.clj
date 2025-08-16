(ns net.mynarz.localquiz.core
  (:gen-class)
  (:require [net.mynarz.localquiz.db :refer [initial-db]]
            [net.mynarz.localquiz.views] ; Must be loaded to register routes.
            [hyperlith.core :as h :refer [defaction defview]]))

(defn ctx-start
  []
  (let [db (atom initial-db)]
    {:db db}))

(defn ctx-stop
  [ctx])

(defonce app
  (atom nil))

(defn -main
  [& args]
  (reset! app
    (h/start-app
      {:ctx-start ctx-start
       :ctx-stop ctx-stop
       :csrf-secret (h/env :csrf-secret)
       :max-refresh-ms 100
       :port (h/env :port)})))

(comment
  ; Start the application
  (-main)

  ; Open the application
  (clojure.java.browse/browse-url (format "http://localhost:%d/" (h/env :port)))

  ; Stop the application
  ((@app :stop))

  ; Get the application's database
  (def db
    (-> @app
        :ctx
        :db)))
