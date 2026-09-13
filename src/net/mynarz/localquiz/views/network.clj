(ns net.mynarz.localquiz.views.network
  "SVG drawing of the network of a :network question, which resources/public/js/network.js
  reveals a neighbourhood at a time."
  (:require [net.mynarz.localquiz.network :as network]
            [clojure.string :as string]
            [dev.onionpancakes.chassis.core :as h]))

; Sizes in screen pixels, mirrored by resources/public/js/network.js. The tap target is
; 44 px across, the usual minimum for touch.
(def ^:private node-radius 8)
(def ^:private target-radius 22)
(def ^:private label-offset 12)

(defn- drawing
  [choices]
  (let [{:keys [arcs nodes]} (network/network choices)
        positions            (network/layout choices)
        root                 (network/root choices)
        choice?              (set (map :label choices))
        ; The root and its children show before network.js takes over.
        shown                (into #{root} (for [[parent child] arcs
                                                 :when (= parent root)]
                                             child))]
    (h/html
      [:svg {:data-root root}
       [:g.nm-viewport
        (for [[a b] arcs
              :let  [[x1 y1] (positions a)
                     [x2 y2] (positions b)]]
          [:line {:class     (cond-> "nm-edge"
                               (not (and (shown a) (shown b))) (str " nm-hidden"))
                  :data-from a
                  :data-to   b
                  :x1        x1
                  :y1        y1
                  :x2        x2
                  :y2        y2}])
        (for [node  nodes
              :let  [[x y] (positions node)
                     left? (neg? x)]]
          [:g {:class      (string/join " " (cond-> ["nm-node"]
                                              (= node root)       (conj "nm-root")
                                              (choice? node)      (conj "nm-choice")
                                              (not (shown node))  (conj "nm-hidden")))
               :data-id    node
               :data-x     x
               :data-y     y
               :transform  (format "translate(%s %s)" x y)
               :tabindex   0
               :role       "button"
               :aria-label node}
           [:g.nm-glyph
            [:circle.nm-target {:r target-radius}]
            [:circle {:r node-radius}]
            [:text {:x           (if left? (- label-offset) label-offset)
                    :dy          "0.35em"
                    :text-anchor (if left? "end" "start")}
             node]]])]])))

(def ^:private drawing-html
  "Every question sharing a network shares the drawing, so it is rendered once."
  (memoize drawing))

(defn- info
  "Cell under the drawing, hidden until a choice is chosen, which it then shows with its
  description, as network.js reveals it."
  [choices]
  (h/html
    [:div.nm-info
     {:hidden true}
     (for [{:keys [description label]} choices]
       [:div.nm-chosen
        {:data-for label
         :hidden   true}
        [:strong label]
        (when description
          [:div.description description])])]))

(def ^:private info-html
  "Constant per question, so rendered once."
  (memoize info))

(defn network-map
  "Map of the network that `choices` describe, dispatching a bubbling `network-select`
  event with the label of each choice tapped, which the cell under it then shows. Morphs
  leave it be, as the state of what it reveals lives in the browser."
  [choices]
  [:div.network-map
   {:data-ignore-morph true
    :data-init "createNetworkMap(el)"}
   (h/raw (drawing-html choices))
   (h/raw (info-html choices))])
