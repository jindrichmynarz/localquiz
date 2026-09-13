(ns net.mynarz.localquiz.network
  "Layout of the directed acyclic graphs that :network questions let players pick answers
  from. The root sits at the centre, arcs point mostly outward, and no two edges cross
  unless the graph is not planar.

  A planar backbone of the graph is triangulated and drawn on a grid without crossings
  (de Fraysseix, Pach and Pollack), then refined by forces that cannot introduce crossings
  (after PrEd, Bertault 2000). `layout-report` measures the result."
  (:require [clojure.string :as string])
  (:import [java.util Arrays]
           [org.jgrapht Graph]
           [org.jgrapht.alg.planar BoyerMyrvoldPlanarityInspector]
           [org.jgrapht.graph DefaultEdge SimpleGraph]))

(def max-nodes
  "Most nodes a network may have. Refinement is quadratic per step."
  300)

; ponytail: hand-tuned. Lengths are in units of the ideal edge length.
(def ^:private ring-gap 1.0)
(def ^:private radial-pull 1.0)
(def ^:private edge-repulsion-range 0.5)
(def ^:private separation 1.2)
(def ^:private separation-push 5.0)
(def ^:private min-gap 1e-6)
(def ^:private iterations 600)
(def ^:private start-temperature 2.0)
(def ^:private median-edge-length
  "Scale of the returned positions: the median arc is drawn this long."
  100.0)
(def ^:private tau (* 2 Math/PI))

(defn- topological-order
  "`nodes` in topological order by `arcs`, or nil if the arcs form a cycle. Kahn's algorithm."
  [nodes arcs]
  (let [children (group-by first arcs)]
    (loop [indegree (reduce (fn [acc [_ child]] (update acc child inc))
                            (zipmap nodes (repeat 0))
                            arcs)
           queue    (into clojure.lang.PersistentQueue/EMPTY (filter (comp zero? indegree) nodes))
           order    []]
      (if-let [node (peek queue)]
        (let [kids     (map second (children node))
              indegree (reduce #(update %1 %2 dec) indegree kids)]
          (recur indegree
                 (into (pop queue) (filter (comp zero? indegree)) kids)
                 (conj order node)))
        (when (= (count order) (count nodes))
          order)))))

(defn- network*
  [choices]
  (let [arcs  (vec (distinct (for [{:keys [label related]} choices
                                   parent related]
                               [parent label])))
        nodes (vec (distinct (concat (mapcat :related choices) (map :label choices))))
        child (set (map second arcs))]
    {:nodes nodes
     :arcs  arcs
     :roots (filterv (complement child) nodes)
     :order (topological-order nodes arcs)}))

(def network
  "The graph `choices` describe, as `{:nodes :arcs :roots :order}`. An arc `[parent child]`
  runs to each choice from every label in its :related. Roots are the nodes without parents,
  and need not be choices. `:order` is topological, or nil when the arcs form a cycle.
  Memoized, as every question sharing the choices asks for it."
  (memoize network*))

(defn single-root?
  [choices]
  (= 1 (count (:roots (network choices)))))

(defn acyclic?
  [choices]
  (some? (:order (network choices))))

(defn within-max-nodes?
  [choices]
  (<= (count (:nodes (network choices))) max-nodes))

(defn root
  [choices]
  (first (:roots (network choices))))

(defn- layers
  "Longest-path depth of each node, so that every arc runs to a strictly deeper layer."
  [{:keys [arcs order]}]
  (let [children (group-by first arcs)]
    (reduce (fn [layer node]
              (reduce (fn [layer [_ child]]
                        (update layer child max (inc (layer node))))
                      layer
                      (children node)))
            (zipmap order (repeat 0))
            order)))

(defn- ring-radii
  "Radius of each layer's ring around the root: at least `ring-gap` beyond the previous one,
  and wide enough to space the layer's nodes `ring-gap` apart."
  [layer]
  (let [counts (frequencies (vals layer))]
    (reduce (fn [radii l]
              (conj radii (max (+ (peek radii) ring-gap)
                               (/ (* (counts l 0) ring-gap) tau))))
            [0.0]
            (range 1 (inc (reduce max (vals layer)))))))

(defn- graph
  "Undirected JGraphT graph of `nodes` and `edges`."
  ^Graph [nodes edges]
  (let [g (SimpleGraph. DefaultEdge)]
    (doseq [node nodes]
      (.addVertex g node))
    (doseq [[a b] edges]
      (.addEdge g a b))
    g))

(defn- graph-edges
  [^Graph g]
  (for [edge (.edgeSet g)]
    [(.getEdgeSource g edge) (.getEdgeTarget g edge)]))

(defn- planar?
  [^Graph g]
  (.isPlanar (BoyerMyrvoldPlanarityInspector. g)))

(defn- adjacency
  "Undirected adjacency map of `edges`."
  [edges]
  (reduce (fn [acc [a b]]
            (-> acc
                (update a (fnil conj #{}) b)
                (update b (fnil conj #{}) a)))
          {}
          edges))

(defn- spanning-tree
  "Edges of a breadth-first spanning tree of `adjacent` rooted at `root`."
  [adjacent root]
  (loop [queue [root]
         seen  #{root}
         tree  []]
    (if-let [node (first queue)]
      (let [kids (remove seen (sort (adjacent node)))]
        (recur (into (subvec queue 1) kids)
               (into seen kids)
               (into tree (map #(vector node %)) kids)))
      tree)))

(defn- distances
  "Breadth-first distance of every node from `root` over the undirected `edges`."
  [edges root]
  (let [adjacent (adjacency edges)]
    (loop [queue    [root]
           distance {root 0}]
      (if-let [node (first queue)]
        (let [kids (remove distance (adjacent node))]
          (recur (into (subvec queue 1) kids)
                 (into distance (map #(vector % (inc (distance node)))) kids)))
        distance))))

(defn nearby
  "Nodes within `max-hops` of `node` in the network `choices` describe, as `{node distance}`,
  following arcs either way. Paths do not pass through the root unless it is a choice: a
  root that only ties the network together says nothing about how close two nodes are."
  [choices node max-hops]
  (let [{:keys [arcs]} (network choices)
        root           (root choices)
        arcs           (if (some (comp #{root} :label) choices)
                         arcs
                         (remove (partial some #{root}) arcs))
        adjacent       (adjacency arcs)]
    (loop [ring     [node]
           distance {node 0}
           hops     0]
      (if (or (empty? ring) (= hops max-hops))
        distance
        (let [ring (distinct (remove distance (mapcat adjacent ring)))]
          (recur ring
                 (into distance (map #(vector % (inc hops))) ring)
                 (inc hops)))))))

(defn- backbone
  "Edges of a planar subgraph of `edges` that spans `nodes`: all of them if they are planar,
  otherwise a maximal planar subgraph grown greedily from a breadth-first spanning tree of
  `root`, which keeps it connected."
  [nodes edges root]
  (if (planar? (graph nodes edges))
    edges
    (let [g (graph nodes (spanning-tree (adjacency edges) root))]
      (doseq [[a b] edges
              :when (not (.containsEdge g a b))]
        (.addEdge g a b)
        (when-not (planar? g)
          (.removeEdge g a b)))
      (graph-edges g))))

(defn- rotation
  "Clockwise order of every node's neighbours in a planar embedding of `g`."
  [^Graph g]
  (let [embedding (.getEmbedding (BoyerMyrvoldPlanarityInspector. g))]
    (into {} (for [node (.vertexSet g)]
               [node (vec (for [edge (.getEdgesAround embedding node)]
                            (let [source (.getEdgeSource g edge)]
                              (if (= source node) (.getEdgeTarget g edge) source))))]))))

(defn- faces
  "Faces of an embedding, each as a vector of half-edges `[from to]`. Walks half-edges:
  after `[u v]` comes `[v w]`, where `w` precedes `u` in `v`'s rotation."
  [rotation]
  (let [next-half (fn [[u v]]
                    (let [around ^java.util.List (rotation v)]
                      [v (around (mod (dec (.indexOf around u)) (count around)))]))]
    (loop [remaining (set (for [[node neighbours] rotation
                                neighbour neighbours]
                            [node neighbour]))
           found     []]
      (if-let [start (first remaining)]
        (let [face (loop [half start
                          acc  []]
                     (let [acc  (conj acc half)
                           next (next-half half)]
                       (if (= next start) acc (recur next acc))))]
          (recur (reduce disj remaining face) (conj found face)))
        found))))

(defn- add-chord!
  "Add to `g` an edge between two not yet adjacent vertices on the boundary of `face`, if
  there are any. Drawn through the face, such a chord crosses nothing."
  [^Graph g face]
  (let [walk (mapv first face)
        n    (count walk)]
    (when-let [[a b] (first (for [i (range n)
                                  j (range (+ i 2) n)
                                  :let  [a (walk i)
                                         b (walk j)]
                                  :when (and (not (and (zero? i) (= j (dec n))))
                                             (not= a b)
                                             (not (.containsEdge g a b)))]
                              [a b]))]
      (.addEdge g a b))))

(defn- triangulate
  "Add chords to the faces of connected planar `g` until every face is a triangle, making
  it maximal planar, which the canonical ordering needs. A chord per face per
  round, so one embedding per round rather than a planarity test per candidate edge.
  Mutates `g`."
  [^Graph g]
  (loop []
    (when (pos? (count (keep (fn [face]
                               (when (> (count face) 3)
                                 (add-chord! g face)))
                             (faces (rotation g)))))
      (recur)))
  g)

(defn- inner-arc
  "Neighbours of `node` strictly between its outer-cycle neighbours `u` and `w` in its
  rotation `around`, on the side away from the `removed` vertices, ordered from `u` to `w`."
  [around u w removed]
  (let [arc      (fn [around]
                   (let [n     (count around)
                         start (.indexOf ^java.util.List around u)]
                     (vec (take-while #(not= % w)
                                      (map #(around (mod (+ start 1 %) n)) (range n))))))
        forward  (arc around)
        backward (arc (vec (rseq around)))]
    (cond
      (some removed forward)  backward
      (some removed backward) forward
      ; Nothing is removed yet, so the outer side is the empty arc: the outer face itself.
      :else                   (max-key count backward forward))))

(defn- canonical-order
  "Canonical ordering of a maximal planar graph with `rotation` and outer face `[v1 v2 vn]`
  (de Fraysseix, Pach and Pollack 1990), as `[order spans]`. Every `v` after the first three
  has its earlier neighbours on a contiguous stretch of the contour of the graph the earlier
  vertices induce, from `u` to `w` for `[u w] (spans v)`. Built backwards, by peeling off a
  vertex of the outer cycle, other than `v1` and `v2`, that is incident to no chord."
  [rotation [v1 v2 vn]]
  (loop [path    [v1 vn v2] ; The outer cycle, less the edge from v2 back to v1
         removed #{}
         order   ()
         spans   {}]
    (if (= 2 (count path))
      [(into [v1 v2] order) spans]
      (let [on-path (set path)
            i       (or (first (for [i     (range 1 (dec (count path)))
                                     :when (= 2 (count (filter on-path (rotation (path i)))))]
                                 i))
                        (throw (ex-info "No vertex to peel off: not maximal planar."
                                        {:path path})))
            [u x w] (subvec path (dec i) (+ i 2))
            removed (conj removed x)]
        (recur (vec (concat (subvec path 0 i)
                            (inner-arc (rotation x) u w removed)
                            (subvec path (inc i))))
               removed
               (cons x order)
               (assoc spans x [u w]))))))

(defn- shift-drawing
  "Straight-line drawing of a maximal planar graph in canonical `order` on a
  (2n - 4) x (n - 2) grid, with no crossings: the shift method of de Fraysseix, Pach and
  Pollack. Each vertex goes above the stretch of the contour it covers, at the apex of
  slopes +1 and -1 from its ends, after the contour right of its left end is shifted
  apart to make room. `under` holds the vertices that move with each contour vertex."
  [[v1 v2 v3 & more] spans]
  (loop [position {v1 [0 0] v2 [2 0] v3 [1 1]}
         under    {v1 [v1] v2 [v2] v3 [v3]}
         contour  [v1 v3 v2]
         more     more]
    (if-let [v (first more)]
      (let [[u w]         (spans v)
            p             (.indexOf ^java.util.List contour u)
            q             (.indexOf ^java.util.List contour w)
            position      (reduce (fn [position i]
                                    (reduce (fn [position x]
                                              (update-in position [x 0] + (if (< i q) 1 2)))
                                            position
                                            (under (contour i))))
                                  position
                                  (range (inc p) (count contour)))
            [xp yp]       (position u)
            [xq yq]       (position w)]
        (recur (assoc position v [(quot (+ xp xq (- yq yp)) 2)
                                  (quot (+ (- xq xp) yp yq) 2)])
               (assoc under v (into [v] (mapcat (comp under contour)) (range (inc p) q)))
               (vec (concat (subvec contour 0 (inc p)) [v] (subvec contour q)))
               (rest more)))
      position)))

(defn- refine
  "Force-directed refinement of `positions` that keeps edges of `backbone` from crossing
  (after PrEd, Bertault 2000). For a node and a backbone edge it is not on, the line through
  the closest point, perpendicular to it, separates the two. Each step, the node may close
  in on that line by less than half their distance, and so may the edge's ends from the
  other side, so neither reaches it. Moving along or away from the edge is free, which lets
  nodes slide past the long edges of the grid drawing.

  Forces: node repulsion, attraction along `arcs`, repulsion of nodes from nearby backbone
  edges, and a radial pull of each node to the ring of its `layer` around `root`."
  [positions nodes arcs backbone layer root]
  (let [n      (count nodes)
        index  (zipmap nodes (range))
        xs     (double-array (map (comp first positions) nodes))
        ys     (double-array (map (comp second positions) nodes))
        ea     (int-array (map (comp index first) arcs))
        eb     (int-array (map (comp index second) arcs))
        ba     (int-array (map (comp index first) backbone))
        bb     (int-array (map (comp index second) backbone))
        radii  (ring-radii layer)
        target (double-array (map (comp radii layer) nodes))
        centre (int (index root))
        fx     (double-array n)
        fy     (double-array n)
        scale  (double-array n)
        gamma  (double edge-repulsion-range)
        gap    (double min-gap)
        pull   (double radial-pull)
        spread (double separation)
        push   (double separation-push)]
    (dotimes [step iterations]
      (Arrays/fill fx 0.0)
      (Arrays/fill fy 0.0)
      (Arrays/fill scale 1.0)
      ; Node repulsion: magnitude 1/d, plus a steep push apart below `separation`, so
      ; that crowded nodes stay far enough apart to tap.
      (dotimes [i n]
        (loop [j (inc i)]
          (when (< j n)
            (let [dx (- (aget xs i) (aget xs j))
                  dy (- (aget ys i) (aget ys j))
                  d2 (max 1e-9 (+ (* dx dx) (* dy dy)))
                  d  (Math/sqrt d2)
                  f  (+ (/ 1.0 d2)
                        (if (< d spread) (/ (* push (- spread d)) d) 0.0))]
              (aset fx i (+ (aget fx i) (* dx f)))
              (aset fy i (+ (aget fy i) (* dy f)))
              (aset fx j (- (aget fx j) (* dx f)))
              (aset fy j (- (aget fy j) (* dy f))))
            (recur (inc j)))))
      ; Arc attraction: magnitude d. Stronger pulls leave more labels overlapping.
      (dotimes [e (alength ea)]
        (let [a  (aget ea e)
              b  (aget eb e)
              dx (- (aget xs b) (aget xs a))
              dy (- (aget ys b) (aget ys a))]
          (aset fx a (+ (aget fx a) dx))
          (aset fy a (+ (aget fy a) dy))
          (aset fx b (- (aget fx b) dx))
          (aset fy b (- (aget fy b) dy))))
      ; Radial pull to the node's ring around the root.
      (dotimes [i n]
        (let [dx (- (aget xs i) (aget xs centre))
              dy (- (aget ys i) (aget ys centre))
              r  (max 1e-9 (Math/sqrt (+ (* dx dx) (* dy dy))))
              f  (/ (* pull (- (aget target i) r)) r)]
          (aset fx i (+ (aget fx i) (* dx f)))
          (aset fy i (+ (aget fy i) (* dy f)))))
      ; Node-edge repulsion: magnitude (gamma - d) / d, which no pull outdoes up close.
      (dotimes [e (alength ba)]
        (let [a    (aget ba e)
              b    (aget bb e)
              ax   (aget xs a)
              ay   (aget ys a)
              dx   (- (aget xs b) ax)
              dy   (- (aget ys b) ay)
              len2 (max 1e-18 (+ (* dx dx) (* dy dy)))]
          (dotimes [v n]
            (when-not (or (== v a) (== v b))
              (let [vx (aget xs v)
                    vy (aget ys v)
                    t  (/ (+ (* (- vx ax) dx) (* (- vy ay) dy)) len2)]
                (when (and (> t 0.0) (< t 1.0))
                  (let [ux (- vx (+ ax (* t dx)))
                        uy (- vy (+ ay (* t dy)))
                        d  (Math/sqrt (+ (* ux ux) (* uy uy)))]
                    (when (and (> d 0.0) (< d gamma))
                      (let [f (/ (- gamma d) (* d d))]
                        (aset fx v (+ (aget fx v) (* ux f)))
                        (aset fy v (+ (aget fy v) (* uy f)))
                        (aset fx a (- (aget fx a) (* 0.5 ux f)))
                        (aset fy a (- (aget fy a) (* 0.5 uy f)))
                        (aset fx b (- (aget fx b) (* 0.5 ux f)))
                        (aset fy b (- (aget fy b) (* 0.5 uy f))))))))))))
      ; Displacements capped by the temperature, then shortened to respect the separating
      ; lines. Shortening keeps the direction, so every limit met stays met.
      (let [temperature (* start-temperature (- 1.0 (/ step (double iterations))))]
        (dotimes [i n]
          (let [f (Math/sqrt (+ (* (aget fx i) (aget fx i)) (* (aget fy i) (aget fy i))))]
            (when (> f temperature)
              (aset fx i (* (aget fx i) (/ temperature f)))
              (aset fy i (* (aget fy i) (/ temperature f)))))))
      (dotimes [e (alength ba)]
        (let [a    (aget ba e)
              b    (aget bb e)
              ax   (aget xs a)
              ay   (aget ys a)
              dx   (- (aget xs b) ax)
              dy   (- (aget ys b) ay)
              len2 (max 1e-18 (+ (* dx dx) (* dy dy)))]
          (dotimes [v n]
            (when-not (or (== v a) (== v b))
              (let [vx (aget xs v)
                    vy (aget ys v)
                    t  (max 0.0 (min 1.0 (/ (+ (* (- vx ax) dx) (* (- vy ay) dy)) len2)))
                    ux (- vx (+ ax (* t dx)))
                    uy (- vy (+ ay (* t dy)))
                    d  (Math/sqrt (+ (* ux ux) (* uy uy)))]
                (if (<= d gap)
                  ; Too close to trust the arithmetic: hold all three still.
                  (do (aset scale v 0.0)
                      (aset scale a 0.0)
                      (aset scale b 0.0))
                  (let [nx     (/ ux d)
                        ny     (/ uy d)
                        ; Just under half the distance.
                        h      (* 0.49 d)
                        toward (- (+ (* (aget fx v) nx) (* (aget fy v) ny)))
                        a-in   (+ (* (aget fx a) nx) (* (aget fy a) ny))
                        b-in   (+ (* (aget fx b) nx) (* (aget fy b) ny))]
                    (when (> toward h) (aset scale v (min (aget scale v) (/ h toward))))
                    (when (> a-in h) (aset scale a (min (aget scale a) (/ h a-in))))
                    (when (> b-in h) (aset scale b (min (aget scale b) (/ h b-in)))))))))))
      (dotimes [i n]
        (aset xs i (+ (aget xs i) (* (aget scale i) (aget fx i))))
        (aset ys i (+ (aget ys i) (* (aget scale i) (aget fy i))))))
    (let [rx (aget xs centre)
          ry (aget ys centre)]
      (zipmap nodes (map (fn [x y] [(- x rx) (- y ry)]) xs ys)))))

(defn- median
  [xs]
  (nth (sort xs) (quot (count xs) 2)))

(defn- layout*
  [choices]
  (let [{:keys [nodes arcs]
         :as   network} (network choices)
        root            (root choices)]
    (if (< (count nodes) 3)
      ; Too few to triangulate: the root and its only child.
      (into {root [0.0 0.0]}
            (for [node nodes :when (not= node root)] [node [median-edge-length 0.0]]))
      (let [layer     (layers network)
            backbone  (backbone nodes arcs root)
            rotation  (rotation (triangulate (graph nodes backbone)))
            distance  (distances arcs root)
            ; The face farthest from the root goes outside, which puts the root well inside.
            outer     (mapv first (apply max-key
                                         (fn [face]
                                           (let [ds (map (comp distance first) face)]
                                             (+ (* 1000 (reduce min ds)) (reduce + ds))))
                                         (faces rotation)))
            grid      (apply shift-drawing (canonical-order rotation outer))
            [rx ry]   (grid root)
            ; Fit the grid drawing, (2n - 4) wide, into the outermost ring.
            fit       (/ (peek (ring-radii layer)) (count nodes))
            centred   (update-vals grid (fn [[x y]] [(* fit (- x rx)) (* fit (- y ry))]))
            positions (refine centred nodes arcs backbone layer root)
            scale     (/ median-edge-length
                         (median (for [[a b] arcs
                                       :let [[ax ay] (positions a)
                                             [bx by] (positions b)]]
                                   (Math/hypot (- bx ax) (- by ay)))))]
        (update-vals positions (fn [[x y]]
                                 [(/ (Math/round (* 10.0 scale x)) 10.0)
                                  (/ (Math/round (* 10.0 scale y)) 10.0)]))))))

(def layout
  "Positions of the nodes of the network `choices` describe, as `{node [x y]}`, with the
  root at `[0 0]`. Deterministic. Memoized, as it takes seconds."
  (memoize layout*))

(defn- ccw
  "Which way the turn `a` -> `b` -> `c` bends: 1 counter-clockwise, -1 clockwise,
  0 collinear. The `^double` hints are not decoration: without them this runs on boxed
  math and `crossings` takes 382 ms instead of 18 ms."
  ^double [[^double ax ^double ay] [^double bx ^double by] [^double cx ^double cy]]
  (Math/signum (- (* (- by ay) (- cx ax)) (* (- bx ax) (- cy ay)))))

(defn crossings
  "Pairs of `edges` that cross when drawn as straight segments between `positions`. Edges
  sharing an endpoint meet at a node rather than crossing."
  [edges positions]
  (let [edges    (vec edges)
        segments (mapv (fn [[a b]] [(positions a) (positions b)]) edges)
        n        (count edges)]
    (for [i (range n)
          j (range (inc i) n)
          :let  [[a b] (edges i)
                 [c d] (edges j)
                 [p1 p2] (segments i)
                 [p3 p4] (segments j)]
          :when (and (not (#{a b} c))
                     (not (#{a b} d))
                     (not= (ccw p1 p2 p3) (ccw p1 p2 p4))
                     (not= (ccw p3 p4 p1) (ccw p3 p4 p2)))]
      [(edges i) (edges j)])))

; Screen geometry of the drawing, in CSS pixels, mirrored by resources/public/js/network.js
; and resources/public/css/style.css. The report assumes a portrait phone.
(def ^:private screen-width 360.0)
(def ^:private screen-height 440.0)
(def ^:private screen-padding 24.0)
(def ^:private node-radius 8.0)
(def ^:private label-offset 12.0)
(def ^:private label-height 19.0)
(def ^:private char-width 8.9)
(def ^:private max-zoom 1.6)
(def ^:private tap-spacing
  "Closest that two nodes on screen may come without making either hard to tap."
  30.0)

(defn- viewport
  "Zoom and offset that fit `nodes` on screen, as `[zoom dx dy]`, a screen point being
  `[(+ dx (* zoom x)) (+ dy (* zoom y))]`, as the front end computes it."
  [positions nodes]
  (let [xs   (map (comp first positions) nodes)
        ys   (map (comp second positions) nodes)
        [x0 x1 y0 y1] [(reduce min xs) (reduce max xs) (reduce min ys) (reduce max ys)]
        zoom (min max-zoom
                  (/ (- screen-width (* 2 screen-padding)) (max 1e-9 (- x1 x0)))
                  (/ (- screen-height (* 2 screen-padding)) (max 1e-9 (- y1 y0))))]
    [zoom
     (- (/ screen-width 2) (* zoom (/ (+ x0 x1) 2)))
     (- (/ screen-height 2) (* zoom (/ (+ y0 y1) 2)))]))

(defn- overlap-area
  [[al ar at ab] [bl br bt bb]]
  (* (max 0.0 (- (min ar br) (max al bl)))
     (max 0.0 (- (min ab bb) (max at bt)))))

(defn- splits
  "Ways to split `words` into `n` runs, in order."
  [words n]
  (if (= n 1)
    [[words]]
    (for [i    (range 1 (- (count words) (dec n) -1))
          more (splits (subvec words i) (dec n))]
      (cons (subvec words 0 i) more))))

(defn- wraps
  "`label` wrapped to 1, 2, ... lines, a word per line at most, each split between words
  so that the longest line is as short as can be."
  [^String label]
  (let [words   (string/split label #" ")
        longest #(reduce max (map count %))]
    (for [n (range 1 (inc (count words)))]
      (reduce (fn [best lines] (if (< (longest lines) (longest best)) lines best))
              (map #(map (partial string/join " ") %) (splits words n))))))

(defn- label-overlaps
  "Nodes among `shown`, in order of priority, whose labels overlap others, go off screen,
  or cover circles at the zoom that fits them. Every label shows, on one line on the side
  facing away from the root, else the other side, else wrapped to ever more lines on
  either, whichever first stays on screen and clear, or else whichever overlaps least. The parents that the front
  end pins to the edge of the screen are left out."
  [positions shown]
  (let [[zoom dx dy] (viewport positions shown)
        screen-at    (fn [node] (let [[x y] (positions node)]
                                  [(+ dx (* zoom x)) (+ dy (* zoom y))]))
        circles      (for [node shown
                           :let [[x y] (screen-at node)]]
                       [node [(- x node-radius) (+ x node-radius) (- y node-radius) (+ y node-radius)]])
        screen       [0.0 screen-width 0.0 screen-height]]
    (loop [placed [] overlapping [] [node & more :as nodes] shown]
      (if (empty? nodes)
        overlapping
        (let [[x y]      (screen-at node)
              layouts    (wraps node)
              sides      (if (neg? (first (positions node))) [:left :right] [:right :left])
              candidates (for [lines layouts
                               side  sides
                               :let  [w (* char-width (reduce max (map count lines)))
                                      h (* label-height (count lines))
                                      l (if (= side :left) (- x label-offset w) (+ x label-offset))
                                      box [l (+ l w) (- y (/ h 2)) (+ y (/ h 2))]]]
                           [box (+ (- (* w h) (overlap-area box screen))
                                   (reduce + (map #(overlap-area box %) placed))
                                   (reduce + (for [[other circle] circles
                                                   :when (not= other node)]
                                               (overlap-area box circle))))])
              ; Under a square pixel is rounding error, not overlap.
              clear?     #(< (second %) 1.0)
              [box cost] (or (first (filter clear? candidates))
                             (reduce (fn [best candidate]
                                       (if (< (second candidate) (second best)) candidate best))
                                     candidates))]
          (recur (conj placed box)
                 (cond-> overlapping (>= cost 1.0) (conj node))
                 more))))))

(defn- closest
  "Shortest distance on screen between two of `shown` at the zoom that fits them."
  [positions shown]
  (let [shown  (vec shown)
        [zoom] (viewport positions shown)]
    (reduce min Double/MAX_VALUE (for [[i a] (map-indexed vector shown)
                                       b     (subvec shown (inc i))
                                       :let  [[ax ay] (positions a)
                                              [bx by] (positions b)]]
                                   (* zoom (Math/hypot (- bx ax) (- by ay)))))))

(defn- priority
  "`nodes` in the order the front end places their labels: `focus` first, then by degree."
  [adjacent focus nodes]
  (cons focus (sort-by (juxt (comp - count adjacent) identity) (remove #{focus} nodes))))

(defn layout-report
  "Quality of the layout of `choices`: time taken, edges left out of the planar backbone,
  crossings, share of arcs pointing away from the root, the shortest distance between two
  nodes relative to the median arc, the labels that overlap when each node is tapped,
  summed, with the number of nodes whose tap leaves any, and the closest two nodes come on
  screen after a tap, with the number of taps that bring two closer than `tap-spacing`."
  [choices]
  (let [start                 (System/nanoTime)
        positions             (layout* choices)
        ms                    (/ (- (System/nanoTime) start) 1e6)
        {:keys [nodes arcs]}  (network choices)
        adjacent              (adjacency arcs)
        norm                  (fn [node] (let [[x y] (positions node)] (Math/hypot x y)))
        distances             (for [[i a] (map-indexed vector nodes)
                                    b     (subvec nodes (inc i))
                                    :let  [[ax ay] (positions a)
                                           [bx by] (positions b)]]
                                (Math/hypot (- bx ax) (- by ay)))
        children              (group-by first arcs)
        parents               (group-by second arcs)
        ; Tapping a node zooms to its children, or to its parents if it has none.
        framed                (fn [focus]
                                (priority adjacent
                                          focus
                                          (or (seq (map second (children focus)))
                                              (map first (parents focus)))))
        overlapping           (map (comp count (partial label-overlaps positions) framed)
                                   nodes)
        spacing               (map (comp (partial closest positions) framed) nodes)]
    {:ms             (Math/round ms)
     :left-out       (- (count arcs) (count (backbone nodes arcs (root choices))))
     :crossings      (count (crossings arcs positions))
     :outward        (double (/ (count (filter (fn [[a b]] (< (norm a) (norm b))) arcs))
                                (count arcs)))
     :min-distance   (/ (reduce min distances) median-edge-length)
     :overlapping-labels (reduce + overlapping)
     :overlapping        (count (filter pos? overlapping))
     :closest            (reduce min spacing)
     :crowded            (count (filter #(< % tap-spacing) spacing))}))
