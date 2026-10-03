(ns net.mynarz.localquiz.views.network
  "The network of a :network question, which <network-map> in
  resources/public/js/components.js reveals a neighbourhood at a time, and the tree of its
  answers."
  (:require [net.mynarz.localquiz.network :as network]
            [charred.api :as charred]
            [clojure.string :as string]))

(defn- network-json
  "The network that `choices` describe, laid out, as the JSON that <network-map> in
  resources/public/js/components.js draws."
  [choices]
  (let [{:keys [arcs nodes]} (network/network choices)
        positions            (network/layout choices)]
    (charred/write-json-str
      {:root    (network/root choices)
       :nodes   (for [node nodes
                      :let [[x y] (positions node)]]
                  {:id node :x x :y y})
       :edges   arcs
       :choices (map #(select-keys % [:label :description]) choices)})))

(def ^:private network-json-memo
  "Every question sharing a network shares it, so it is computed once."
  (memoize network-json))

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
  <network-map> is involved and nothing here carries an nm- class."
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
  event with the label of each choice tapped. Morphs leave it be, as it renders into
  itself and the state of what it reveals lives in the browser."
  [tr choices]
  [:network-map
   {:network (network-json-memo choices)
    :hint (tr [:network-hint])
    :hint-pointer (tr [:network-hint-pointer])
    :data-ignore-morph ""}])
