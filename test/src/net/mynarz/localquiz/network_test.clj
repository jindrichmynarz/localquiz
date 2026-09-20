(ns net.mynarz.localquiz.network-test
  (:require [net.mynarz.localquiz.network :as network]
            [clojure.test :refer [deftest is testing]]))

(defn- choices
  "Choices describing the network of `arcs`, each `[parent child]`."
  [arcs]
  (vec (for [[child arcs] (group-by second arcs)]
         {:label child
          :related (mapv first arcs)})))

(def ^:private fans
  "A planar network: the root's children form a path, and each has a fan of grandchildren
  that form a path too."
  (choices (concat (for [i (range 6)]
                     ["root" (str "c" i)])
                   (for [i (range 5)]
                     [(str "c" i) (str "c" (inc i))])
                   (for [i (range 6)
                         j (range 4)]
                     [(str "c" i) (str "g" i "-" j)])
                   (for [i (range 6)
                         j (range 3)]
                     [(str "g" i "-" j) (str "g" i "-" (inc j))]))))

(deftest validation
  (testing "A network needs one root"
    (is (network/single-root? fans))
    (is (not (network/single-root? (choices [["a" "b"] ["c" "d"]])))))
  (testing "A network may loop, but every node must be reachable from its root"
    (is (network/connected? fans))
    (is (network/connected? (choices [["root" "a"] ["a" "b"] ["b" "a"]])))
    (is (not (network/connected? (choices [["root" "a"] ["b" "c"] ["c" "b"]])))))
  (testing "A network is capped in size"
    (is (network/within-max-nodes? fans))
    (is (not (network/within-max-nodes? (choices (for [i (range network/max-nodes)]
                                                   ["root" (str i)])))))))

(defn- tree
  "The answer tree as `[#{node} #{#{a b}}]`, the arcs as sets so that a test need not know
  which way round they came out."
  [choices terminals]
  (let [{:keys [nodes arcs]} (network/answer-tree choices terminals)]
    [(set nodes) (set (map set arcs))]))

(deftest answer-tree
  (testing "One answer is a tree of one node and no arcs"
    (is (= [#{"g0-2"} #{}] (tree fans ["g0-2"]))))
  (testing "No answers draw nothing"
    (is (= [#{} #{}] (tree fans []))))
  (testing "Answers that name no node in the network are dropped"
    (is (= [#{} #{}] (tree fans ["Atlantis"])))
    (is (= [#{"g0-2"} #{}] (tree fans ["g0-2" "Atlantis"]))))
  (testing "The same answer twice is still one node"
    (is (= [#{"g0-2"} #{}] (tree fans ["g0-2" "g0-2"]))))
  (testing "Neighbouring answers are joined directly, with nothing else drawn"
    (is (= [#{"g0-1" "g0-2"} #{#{"g0-1" "g0-2"}}]
           (tree fans ["g0-1" "g0-2"]))))
  (testing "Answers apart are joined through whatever lies between them"
    ;; A chain, so that only one path can join the two — in `fans` a grandchild pair has
    ;; two shortest paths, along the fan and back through the parent.
    (let [chain (choices [["root" "a"] ["a" "b"] ["b" "c"] ["c" "d"]])]
      (is (= [#{"a" "b" "c" "d"} #{#{"a" "b"} #{"b" "c"} #{"c" "d"}}]
             (tree chain ["a" "d"])))))
  (testing "The root is drawn when it is the only thing holding the answers together"
    (let [star (choices [["root" "a"] ["root" "b"]])]
      (is (= [#{"root" "a" "b"} #{#{"root" "a"} #{"root" "b"}}]
             (tree star ["a" "b"])))))
  (testing "The root is left out when the answers reach each other without it"
    (let [chain (choices [["root" "a"] ["a" "b"] ["b" "c"]])]
      (is (= [#{"a" "b" "c"} #{#{"a" "b"} #{"b" "c"}}]
             (tree chain ["a" "c"])))))
  (testing "Answers in different branches meet at their nearest common node"
    ;; c0 joins its own fan to c1's, so neither root nor the far fans are drawn.
    (is (= [#{"g0-0" "c0" "c1" "g1-0"}
            #{#{"g0-0" "c0"} #{"c0" "c1"} #{"c1" "g1-0"}}]
           (tree fans ["g0-0" "g1-0"]))))
  (testing "Every answer reaches the tree, and nothing dangles off it"
    (let [terminals               ["g0-3" "g3-0" "g5-2" "c2"]
          {:keys [nodes arcs]}    (network/answer-tree fans terminals)
          degree                  (frequencies (mapcat identity arcs))]
      (is (every? (set nodes) terminals))
      (testing "it is a tree: one fewer arc than nodes"
        (is (= (dec (count nodes)) (count arcs))))
      (testing "every leaf is an answer"
        (is (every? (set terminals)
                    (for [[node connections] degree
                          :when (= 1 connections)]
                      node))))))
  (testing "Picking everything spans the whole network"
    (let [all                  (:nodes (network/network fans))
          {:keys [nodes arcs]} (network/answer-tree fans all)]
      (is (= (set all) (set nodes)))
      (is (= (dec (count all)) (count arcs)))))
  (testing "A loop comes back as a tree"
    (let [looped (choices [["root" "a"] ["a" "b"] ["b" "c"] ["c" "a"]])
          {:keys [nodes arcs]} (network/answer-tree looped ["a" "b" "c"])]
      (is (= #{"a" "b" "c"} (set nodes)))
      (is (= 2 (count arcs))))))

(deftest layout
  (let [positions (network/layout fans)
        radius    (fn [node] (apply #(Math/hypot %1 %2) (positions node)))]
    (testing "The root sits in the middle"
      (is (= [0.0 0.0] (positions "root"))))
    (testing "Every arc points outward"
      (is (every? (fn [[a b]] (< (radius a) (radius b)))
                  (:arcs (network/network fans)))))
    (testing "No two nodes share a place"
      (is (= (count positions) (count (set (vals positions)))))))
  (testing "Of a network that loops, every arc but the one closing the loop points outward"
    (let [looped    (choices [["root" "a"] ["a" "b"] ["b" "c"] ["c" "a"]])
          positions (network/layout looped)
          radius    (fn [node] (apply #(Math/hypot %1 %2) (positions node)))]
      (is (= #{"root" "a" "b" "c"} (set (keys positions))))
      (is (every? (fn [[a b]] (< (radius a) (radius b)))
                  (remove #{["c" "a"]} (:arcs (network/network looped)))))))
  (testing "A network of the root and a single child"
    (is (= {"root" [0.0 0.0] "a" [100.0 0.0]}
           (network/layout (choices [["root" "a"]]))))))
