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

; Sizes for the result tree, in units of the drawing's own extent rather than screen
; pixels: the tree is fitted to its box, so a spread of answers is drawn at a smaller
; scale than a tight one, and anything set in pixels would shrink or swell with it. One
; unit is a hundredth of the longer side, so these read as percentages of the drawing.
(def ^:private result-unit 100)
(def ^:private result-node-radius 2.2)
(def ^:private result-max-radius 5.5)
(def ^:private result-font-size 7.0)
(def ^:private result-label-gap 3.0)

; Everyone answering the same, or all along one line, leaves the tree with no extent of
; its own in one or both directions. Falling back to some token size would make the unit
; above a fraction of nothing and draw the node as a speck; these are in the units the
; layout itself works in, where a ring gap is 100, so a lone answer comes out the size it
; would be in any other tree. Wider than tall because the labels run sideways.
(def ^:private result-least-width 100.0)
(def ^:private result-least-height 60.0)

(defn- extent
  "Bounding box of `points` as `[min-x min-y width height]`, never smaller than the least
  above, so that a tree of one node, or a straight line of them, still has a box."
  [points]
  (let [xs     (map first points)
        ys     (map second points)
        x0     (apply min xs)
        x1     (apply max xs)
        y0     (apply min ys)
        y1     (apply max ys)
        width  (max (- x1 x0) result-least-width)
        height (max (- y1 y0) result-least-height)]
    ; Re-centre on the box actually used, so a degenerate run of nodes sits in the middle.
    [(- (/ (+ x0 x1) 2) (/ width 2))
     (- (/ (+ y0 y1) 2) (/ height 2))
     width
     height]))

(defn result-tree
  "The smallest tree joining the answers in `answer-frequencies`, drawn to fit its box.
  Every node is named, the answers among them marked out by a circle that grows with the
  share of `answer-count` that took it — how popular an answer was is a matter of degree,
  which a size carries and a number interrupts the drawing to say.

  Static: no panning, no zooming, nothing revealed a step at a time, so none of
  network.js is involved and nothing here carries an nm- class."
  [choices answer-frequencies answer-count]
  (let [{:keys [arcs nodes]}       (network/answer-tree choices (keys answer-frequencies))
        positions                  (network/layout choices)
        picked                     (set (keys answer-frequencies))
        root                       (network/root choices)
        [x y width height]         (extent (map positions nodes))
        ; One unit of the sizes above, so the glyphs keep their apparent size whether the
        ; answers landed next to each other or at opposite ends of the network.
        unit                       (/ (max width height) result-unit)
        ; Room for the labels, which sit outside the nodes at the edges of the box.
        margin                     (* unit (+ result-font-size result-label-gap))
        neighbours                 (reduce (fn [acc [a b]]
                                             (-> acc
                                                 (update a (fnil conj []) b)
                                                 (update b (fnil conj []) a)))
                                           {}
                                           arcs)
        ; Which side of its node a label sits on. Away from whatever the node is joined
        ; to, rather than away from the root: a label put on the root's side lands on the
        ; very neighbour the arc runs to, which is how "Ambient" ended up written across
        ; "Dark Ambient". A node with nothing either side falls back to the root.
        left?                      (fn [node]
                                     (let [[nx _] (positions node)
                                           lean   (reduce + 0.0
                                                          (for [other (neighbours node)
                                                                :let  [[ox oy] (positions other)
                                                                       dx      (- ox nx)
                                                                       dy      (- oy (second (positions node)))
                                                                       length  (max 1e-9 (Math/hypot dx dy))]]
                                                            (/ dx length)))]
                                       (if (< 0.05 (Math/abs lean))
                                         (pos? lean)
                                         (neg? nx))))
        radius                     (fn [node]
                                     (* unit
                                        (if-let [took (answer-frequencies node)]
                                          (+ result-node-radius
                                             (* (- result-max-radius result-node-radius)
                                                (/ (double took) (max answer-count 1))))
                                          ; Smaller: nobody picked it, it only joins.
                                          (* result-node-radius 0.7))))]
    (when (seq nodes)
      [:svg.nt
       {:viewBox             (string/join " " [(- x margin) (- y margin)
                                               (+ width (* 2 margin)) (+ height (* 2 margin))])
        :preserveAspectRatio "xMidYMid meet"
        :role                "img"}
       (for [[a b] arcs
             :let  [[x1 y1] (positions a)
                    [x2 y2] (positions b)]]
         [:line.nt-edge {:x1 x1 :y1 y1 :x2 x2 :y2 y2}])
       (for [node  nodes
             :let  [[nx ny] (positions node)
                    left?   (left? node)]]
         [:g {:class (string/join " " (cond-> ["nt-node"]
                                        (picked node) (conj "nt-picked")
                                        (= node root) (conj "nt-root")))}
          [:circle {:cx nx :cy ny :r (radius node)}]
          ; Every node is named, the ones between the answers included: they are the
          ; reason the answers are as far apart as they are, and a dot cannot say so.
          [:text.nt-label
           {:x           (if left?
                           (- nx (+ (radius node) (* unit result-label-gap)))
                           (+ nx (radius node) (* unit result-label-gap)))
            :y           ny
            :dy          "0.35em"
            :font-size   (* unit result-font-size)
            :text-anchor (if left? "end" "start")}
           node]])])))

(defn network-map
  "Map of the network that `choices` describe, dispatching a bubbling `network-select`
  event with the label of each choice tapped, which the cell under it then shows. Morphs
  leave it be, as the state of what it reveals lives in the browser."
  [tr choices]
  [:div.network-map
   {:data-ignore-morph true
    :data-init "createNetworkMap(el)"}
   (h/raw (drawing-html choices))
   ; Nothing else says the map pans, there being no cursor on touch. network.js takes the
   ; hint away once it has been heeded. Pointer gestures, so it is not announced.
   [:div.nm-hint {:aria-hidden true}
    [:span.nm-hint-touch (tr [:network-hint])]
    [:span.nm-hint-pointer (tr [:network-hint-pointer])]]
   (h/raw (info-html choices))])
