(ns net.mynarz.localquiz.views
  (:require [net.mynarz.localquiz.css :refer [css]]
            [hyperlith.core :as h :refer [defview]]))

(def shim-headers
  (h/html
    [:link#css {:rel "stylesheet" :type "text/css" :href css}]
    [:title "Localquiz"]))

(defview pick-questions
  {:path "/"
   :shim-headers shim-headers}
  [{:keys [sid]
    :as request}]
  (h/html [:h1 "Localquiz"]))
          ; [:footer
          ;  [:p "Vyrobil " [:a {:href "https://mynarz.net/#jindrich"} "Jindřich Mynarz"]]
          ;  [:p [:a
          ;       {:href "https://github.com/jindrichmynarz/localquiz"}
          ;       "Zdrojový kód"]]]))
