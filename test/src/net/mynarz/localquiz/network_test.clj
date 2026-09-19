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
