(ns net.mynarz.localquiz.network
  "Layout of the directed graphs that :network questions let players pick answers from: a
  radial layered drawing, after Bachmaier's radial adaptation of Sugiyama's framework. Each
  node sits on the ring of its depth around the root, every arc but those closing a loop
  runs outward, and barycentre sweeps order the rings to reduce crossings.
  <network-map> in resources/public/js/components.js lays out each view it shows around
  the directions this layout gives.")

(def max-nodes
  "Most nodes a network may have. Counting crossings, which picks the best sweep, is
  quadratic in the number of arcs."
  300)

(def ^:private ring-gap
  "Gap between rings, and the least spacing of the nodes along a ring, in units of `scale`."
  1.0)
(def ^:private sweeps
  "Rounds of barycentre sweeps, down the rings and back up."
  8)
(def ^:private scale
  "Length of the gap between rings in the returned positions."
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

(defn- forward-arcs
  "`arcs` less those that close a loop: the arcs into a node on the current path of a
  depth-first search from `root`, which takes the children of each node in order."
  [arcs root]
  (let [children (group-by first arcs)
        visit    (fn visit [[seen back] node path]
                   (reduce (fn [[seen back] [_ child :as arc]]
                             (cond (path child) [seen (conj back arc)]
                                   (seen child) [seen back]
                                   :else        (visit [seen back] child (conj path child))))
                           [(conj seen node) back]
                           (children node)))
        [_ back] (visit [#{} #{}] root #{root})]
    (vec (remove back arcs))))

(defn- network*
  [choices]
  (let [arcs    (vec (distinct (for [{:keys [label related]} choices
                                     parent related]
                                 [parent label])))
        nodes   (vec (distinct (concat (mapcat :related choices) (map :label choices))))
        child   (set (map second arcs))
        roots   (filterv (complement child) nodes)
        forward (forward-arcs arcs (first roots))]
    {:nodes   nodes
     :arcs    arcs
     :roots   roots
     :forward forward
     :order   (topological-order nodes forward)}))

(def network
  "The graph `choices` describe, as `{:nodes :arcs :roots :forward :order}`. An arc
  `[parent child]` runs to each choice from every label in its :related. Roots are the
  nodes without parents, and need not be choices. `:forward` are the arcs that do not close
  a loop, as seen from the first root, and `:order` is topological by them, or nil when a
  loop lies out of that root's reach. Memoized, as every question sharing the choices asks
  for it."
  (memoize network*))

(defn single-root?
  [choices]
  (= 1 (count (:roots (network choices)))))

(defn- connected?*
  [choices]
  (let [{:keys [nodes arcs roots]} (network choices)
        children                   (group-by first arcs)]
    (= (count nodes)
       (count (loop [seen  (set (take 1 roots))
                     queue (vec (take 1 roots))]
                (if-let [node (first queue)]
                  (let [kids (remove seen (map second (children node)))]
                    (recur (into seen kids) (into (subvec queue 1) kids)))
                  seen))))))

(def connected?
  "Whether every node can be reached from the root, so that there is a way to it.
  Memoized, like `network`, as every question sharing the choices asks for it."
  (memoize connected?*))

(defn within-max-nodes?
  [choices]
  (<= (count (:nodes (network choices))) max-nodes))

(defn root
  [choices]
  (first (:roots (network choices))))

(defn- layers
  "Longest-path depth of each node, so that every forward arc runs to a strictly deeper
  layer."
  [{:keys [forward order]}]
  (let [children (group-by first forward)]
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

(defn- adjacency
  "Undirected adjacency map of `edges`."
  [edges]
  (reduce (fn [acc [a b]]
            (-> acc
                (update a (fnil conj #{}) b)
                (update b (fnil conj #{}) a)))
          {}
          edges))

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

(defn- shortest-paths
  "Breadth-first from `source` over the undirected `adjacent`, as
  `{node [distance predecessor]}`, the predecessor being the step back toward `source`."
  [adjacent source]
  (loop [ring [source]
         seen {source [0 nil]}
         hops 0]
    (if (empty? ring)
      seen
      (let [step  (inc hops)
            ; Reached from several nodes at once, the first predecessor wins: any of them
            ; lies on a path of the same length, and taking one keeps the result a tree.
            found (reduce (fn [found node]
                            (reduce (fn [found neighbour]
                                      (if (or (seen neighbour) (found neighbour))
                                        found
                                        (assoc found neighbour [step node])))
                                    found
                                    (adjacent node)))
                          {}
                          ring)]
        (recur (vec (keys found)) (into seen found) step)))))

(defn- walk-back
  "The nodes from the source of `paths` to `target`, inclusive."
  [paths target]
  (loop [node target
         path (list target)]
    (if-let [predecessor (second (paths node))]
      (recur predecessor (conj path predecessor))
      path)))

(defn- spanning-tree
  "Edges of `edges` that reach a node not yet reached, so that a subgraph carrying a loop
  comes back as a tree. `edges` must be connected, which a union of paths between the same
  terminals is."
  [edges]
  (let [adjacent (adjacency edges)
        start    (first (sort (keys adjacent)))]
    (loop [queue [start]
           seen  #{start}
           kept  []]
      (if-let [node (first queue)]
        (let [fresh (remove seen (sort (adjacent node)))]
          (recur (into (subvec queue 1) fresh)
                 (into seen fresh)
                 (into kept (map #(vector node %)) fresh)))
        kept))))

(defn- trim
  "Drops leaves that are not terminals, and keeps dropping: a shortest path may overshoot
  past the terminal it was aiming at, and the stub it leaves says nothing."
  [edges terminals]
  (loop [edges (set edges)]
    (let [degree (frequencies (mapcat identity edges))
          stubs  (set (for [[node connections] degree
                            :when (and (= 1 connections) (not (terminals node)))]
                        node))]
      (if (empty? stubs)
        edges
        (recur (into #{} (remove #(some stubs %)) edges))))))

(defn answer-tree
  "Smallest tree connecting `terminals` in the network `choices` describe, as
  `{:nodes :arcs}`. Nodes other than the terminals appear only where one is needed to join
  them, the root included: unlike `nearby`, which drops a root that is not a choice, the
  drawing needs whatever actually holds the answers together.

  Connecting a subset of a graph's nodes as cheaply as possible is the Steiner tree
  problem, so this is its usual approximation: shortest paths between every pair of
  terminals, a minimum spanning tree over those distances, then each of its edges expanded
  back into the path it stands for. With every node picked it reduces to that spanning tree
  alone. Not memoized, as it turns on the answers rather than the choices."
  [choices terminals]
  (let [{:keys [nodes arcs]} (network choices)
        known                (set nodes)
        terminals            (filterv known (distinct terminals))
        picked               (set terminals)]
    (if (< (count terminals) 2)
      {:nodes (vec terminals)
       :arcs  []}
      (let [adjacent (adjacency arcs)
            paths    (into {} (map (juxt identity (partial shortest-paths adjacent))) terminals)
            ; Prim over the terminals alone, the distances standing in for edges. Ties break
            ; on the labels, so the same answers always draw the same tree.
            chosen   (loop [joined #{(first terminals)}
                            apart  (set (rest terminals))
                            chosen []]
                       (if (empty? apart)
                         chosen
                         (let [[_ a b] (->> (for [a     joined
                                                  b     apart
                                                  :let  [[distance] (get-in paths [a b])]
                                                  :when distance]
                                              [distance a b])
                                            sort
                                            first)]
                           (if b
                             (recur (conj joined b) (disj apart b) (conj chosen [a b]))
                             chosen))))
            arc-set  (set arcs)
            orient   (fn [[a b]] (if (arc-set [a b]) [a b] [b a]))
            expanded (into #{}
                           (mapcat (fn [[a b]]
                                     (let [path (walk-back (paths a) b)]
                                       (map orient (map vector path (rest path))))))
                           chosen)
            ; Back to the network's own direction: both the walk out from a terminal and
            ; the sweep in `spanning-tree` run over undirected edges, so either may hand
            ; back an arc the wrong way round for `arcs` to recognise.
            kept     (into #{} (map orient) (trim (spanning-tree expanded) picked))
            reached  (into picked (mapcat identity) kept)]
        ; Both in the network's own order, so the drawing is stable across reveals.
        {:nodes (filterv reached nodes)
         :arcs  (filterv kept arcs)}))))

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

(defn- circular-mean
  [angles]
  (Math/atan2 (reduce + (map #(Math/sin %) angles))
              (reduce + (map #(Math/cos %) angles))))

(defn- isotonic
  "Least-squares non-decreasing fit of `xs`, by pooling adjacent violators."
  [xs]
  (->> xs
       (reduce (fn [blocks x]
                 (loop [blocks (conj blocks [x 1])]
                   (let [[m2 n2] (peek blocks)
                         [m1 n1] (peek (pop blocks))]
                     (if (and m1 (> m1 m2))
                       (recur (conj (pop (pop blocks))
                                    [(/ (+ (* m1 n1) (* m2 n2)) (+ n1 n2)) (+ n1 n2)]))
                       blocks))))
               [])
       (mapcat (fn [[m n]] (repeat n m)))))

(defn- ring-angles
  "Angles of the nodes of `ring`, each as near the angle `key` gives it as it can be while
  staying `gap` apart from its neighbours around the circle, in order."
  [ring key gap]
  (let [n       (count ring)
        gap     (min gap (/ tau n))
        keys    (into {} (map (juxt identity #(mod (key %) tau))) ring)
        ; Stable, so that ties keep the ring's order.
        ordered (vec (sort-by keys ring))
        as      (mapv keys ordered)
        ; The circle is cut at the widest gap between neighbours and unrolled from there.
        cut     (apply max-key #(mod (- (as (mod (inc %) n)) (as %)) tau) (range n))
        is      (map #(mod (+ cut %) n) (range 1 (inc n)))
        unrolled (map #(+ (as %) (if (<= % cut) tau 0)) is)
        ; Least displacement that leaves `gap` between consecutive angles.
        spaced  (map-indexed (fn [k x] (+ x (* k gap)))
                             (isotonic (map-indexed (fn [k a] (- a (* k gap))) unrolled)))]
    (zipmap (map ordered is) spaced)))

(defn- layout*
  [choices]
  (let [{:keys [nodes arcs forward]
         :as   network} (network choices)
        root            (root choices)
        layer           (layers network)
        rings           (group-by layer nodes)
        depth           (reduce max (vals layer))
        radii           (ring-radii layer)
        parents         (update-vals (group-by second forward) #(map first %))
        children        (update-vals (group-by first forward) #(map second %))
        ; Orders the rings `ls` in turn by the mean angle of each node's `neighbours`.
        sweep           (fn [angle ls neighbours]
                          (reduce (fn [angle l]
                                    (merge angle
                                           (ring-angles (rings l)
                                                        #(if-let [ns (seq (neighbours %))]
                                                           (circular-mean (map angle ns))
                                                           (angle %))
                                                        (/ ring-gap (radii l)))))
                                  angle
                                  ls))
        down            #(sweep % (range 1 (inc depth)) parents)
        up              #(sweep % (range (dec depth) 0 -1) children)
        positions       (fn [angle]
                          (into {} (for [node nodes
                                         :let [r (* scale (radii (layer node)))
                                               a (angle node)]]
                                     [node [(* r (Math/cos a)) (* r (Math/sin a))]])))
        score           #(count (crossings arcs (positions %)))
        ; Each ring first in the order its nodes appear in, then the sweeps, keeping
        ; the round with the fewest crossings.
        best            (loop [angle (down {root 0.0})
                               best  angle
                               least (score angle)
                               round 0]
                          (if (= round sweeps)
                            best
                            (let [angle (down (up angle))
                                  n     (score angle)]
                              (if (< n least)
                                (recur angle angle n (inc round))
                                (recur angle best least (inc round))))))]
    (update-vals (positions best)
                 (fn [[x y]]
                   [(/ (Math/round (* 10.0 x)) 10.0)
                    (/ (Math/round (* 10.0 y)) 10.0)]))))

(def layout
  "Positions of the nodes of the network `choices` describe, as `{node [x y]}`, with the
  root at `[0 0]` and each node on the ring of its depth around it. Deterministic.
  Memoized, as every question sharing the choices asks for it."
  (memoize layout*))
